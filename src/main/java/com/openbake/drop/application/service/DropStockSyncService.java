package com.openbake.drop.application.service;

import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.port.StockReservationPort;
import com.openbake.drop.domain.repository.DropEntryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 드롭 재고 카운터(Redis)와 product_inventory(DB) 사이의 수명주기를 관리한다.
 *
 * 드롭이 진행되는 동안 재고의 정본은 Redis 이고 DB는 파생값이다.
 * 복구 원본은 drop_entry 이며, 초기화·검증 모두 그 합계를 기준으로 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DropStockSyncService {

    // 종료된 드롭의 키가 무한히 남지 않도록 dropEnd 이후 여유를 둔다.
    private static final Duration TTL_MARGIN = Duration.ofHours(1);

    private final StockReservationPort stockReservationPort;
    private final DropEntryRepository dropEntryRepository;
    private final ProductPort productPort;
    private final MeterRegistry meterRegistry;

    /**
     * 드롭 시작 시 재고 카운터를 워밍업한다.
     *
     * 반드시 조건부(SET NX)여야 한다. TodayDropCache.refresh() 는 CachedDrop 을 새로 만들면서
     * tryMarkStarted() 마킹을 초기화하는데, 이 경로가 앱 재기동·자정·당일 드롭 등록/수정/삭제로
     * 트리거된다. 무조건 SET 하면 진행 중인 드롭의 잔여 수량이 뒤처진 DB 값으로 덮어써지면서
     * 이미 선점된 재고가 되살아나 초과 판매로 이어진다.
     *
     * 초기값을 remain_quantity 가 아니라 drop_entry 합계로 계산하는 이유도 같다.
     * 이 식은 드롭 시작 시점(합계 0)과 카운터 유실 후 복구 시점 모두에서 정확한 값을 준다.
     */
    @Transactional(readOnly = true)
    public void warmUp(CachedDrop drop) {
        int remain = calculateRemain(drop);
        Duration ttl = Duration.between(LocalDateTime.now(), drop.dropEnd()).plus(TTL_MARGIN);

        if (ttl.isNegative() || ttl.isZero()) {
            log.warn("이미 종료된 드롭이라 재고 카운터를 초기화하지 않습니다. dropId={}", drop.dropId());
            return;
        }

        boolean initialized = stockReservationPort.initIfAbsent(drop.dropId(), remain, ttl);

        if (initialized) {
            log.info("드롭 재고 카운터 초기화. dropId={}, remain={}", drop.dropId(), remain);
        } else {
            log.debug("재고 카운터가 이미 있어 초기화를 건너뜁니다. dropId={}", drop.dropId());
        }
    }

    /**
     * 진행 중 주기 동기화. 드롭당 UPDATE 1회라 경합이 없다.
     *
     * 여기서는 DB를 읽지 않는다. Redis 값을 절대값으로 대입할 뿐이고,
     * 대조 검사는 훨씬 낮은 빈도로 도는 checkDrift 가 맡는다.
     */
    @Transactional
    public void sync(CachedDrop drop) {
        Long remain = stockReservationPort.peek(drop.dropId());

        if (remain == null) {
            return; // 아직 초기화 전이거나 이미 정리됨
        }

        productPort.syncRemainQuantity(drop.productId(), remain.intValue());
    }

    /**
     * 재고 카운터와 drop_entry 합계의 불일치 검출.
     *
     * sync 에서 분리한 이유는 비용과 목적이 다르기 때문이다.
     * 이 검사는 로그만 남기고 아무것도 고치지 않는 관측 로직인데,
     * 근거가 되는 sumReservedQuantity 는 해당 드롭의 RESERVED 행을 전부 훑는 집계다.
     * entry_status 와 select_quantity 가 인덱스에 없어 매칭 행마다 힙을 다시 읽어야 하므로
     * 참여자가 늘수록(= 부하가 가장 높은 순간) 무거워진다.
     * 자가 치유도 못 하는 검사를 그 비용으로 2초마다 돌릴 이유가 없다.
     */
    @Transactional(readOnly = true)
    public void checkDrift(CachedDrop drop) {
        Long remain = stockReservationPort.peek(drop.dropId());

        if (remain == null) {
            return; // 아직 초기화 전이거나 이미 정리됨
        }

        detectDrift(drop, remain);
    }

    /** 드롭 종료 시 최종 확정 후 키를 정리한다. */
    @Transactional
    public void finalizeStock(CachedDrop drop) {
        Long remain = stockReservationPort.peek(drop.dropId());

        // 카운터가 이미 사라졌으면 drop_entry 기준으로 확정한다.
        int finalRemain = remain != null ? remain.intValue() : calculateRemain(drop);

        productPort.syncRemainQuantity(drop.productId(), finalRemain);
        stockReservationPort.clear(drop.dropId());

        log.info("드롭 재고 확정. dropId={}, remain={}", drop.dropId(), finalRemain);
    }

    private int calculateRemain(CachedDrop drop) {
        int total = productPort.getTotalQuantity(drop.productId());
        int reserved = dropEntryRepository.sumReservedQuantity(drop.dropId());

        return Math.max(total - reserved, 0);
    }

    /**
     * drop_entry 합계와 대조해 불일치를 검출한다.
     *
     * 검출만 하고 Redis 를 자동 보정하지는 않는다. 진행 중에 카운터를 덮어쓰는 것은
     * warmUp 이 조건부여야 하는 이유와 같은 위험을 만들기 때문이다(선점 중인 요청과 경합).
     * DB 는 sync 의 syncRemainQuantity 로 어차피 Redis 값에 맞춰지고,
     * 종료 시 finalizeStock 이 최종값을 확정한다.
     */
    private void detectDrift(CachedDrop drop, long redisRemain) {
        int expected = calculateRemain(drop);

        if (expected != redisRemain) {
            // dropId는 계속 늘어나는 값이라 metric label로 쓰지 않는다. 발생 빈도만 집계하고
            // 어느 드롭인지는 로그에서 찾는다.
            meterRegistry.counter("openbake.drop.stock.drift").increment();
            log.warn("재고 카운터와 drop_entry 합계가 다릅니다. dropId={}, redis={}, drop_entry기준={}",
                    drop.dropId(), redisRemain, expected);
        }
    }
}