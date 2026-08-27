package com.openbake.drop.application.service;

import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.port.StockReservationPort;
import com.openbake.drop.domain.entity.Drop;
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
    private final DropService dropService;
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
        int remain = calculateRemain(drop.dropId(), drop.productId());
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
     * 카운터가 없으면(remain == null) DB를 읽지 않고 넘어가던 것을, 재워밍업 시도로 바꿨다.
     * finalizeStock이 dropEnd + 유예 시간 뒤로 미뤄졌으므로(DropStockSyncService.finalizeStock
     * 참고), 이 메서드가 불리는 [dropStart, dropEnd] 구간 안에서 카운터가 없다는 건 이제
     * "아직 초기화 전"이거나 "진행 중 Redis가 유실됨" 둘 중 하나일 수밖에 없다 — 두 경우 모두
     * warmUp으로 복구된다. warmUp은 initIfAbsent(SET NX) 기반이라 반복 호출해도 안전하고,
     * drop_entry 합계로 값을 계산하므로 유실 복구값도 정확하다. 새 스케줄러 없이 기존 2초
     * 주기를 재해석한 것이라 복구까지 최대 2초면 충분하다.
     *
     * 값이 있을 때는 이전과 동일하게 절대값 대입만 한다. 대조 검사는 훨씬 낮은 빈도로 도는
     * checkDrift 가 맡는다.
     */
    @Transactional
    public void sync(CachedDrop drop) {
        Long remain = stockReservationPort.peek(drop.dropId());

        if (remain == null) {
            warmUp(drop);
            return;
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
            // 아직 초기화 전이거나 진행 중 Redis가 유실된 상태. 복구는 이 메서드의 일이 아니다 —
            // sync가 2초 주기로 이미 재워밍업을 시도하므로(DropStockSyncService.sync 참고)
            // 30초 주기인 여기서 다시 손대지 않는다.
            return;
        }

        detectDrift(drop, remain);
    }

    /**
     * 드롭 종료 후 재고를 최종 확정하고 Redis 키를 정리한다.
     *
     * DropStockFinalizeScheduler가 dropEnd로부터 유예 시간이 지난 뒤에만 호출한다 — 그 유예가
     * 진행 중이던 주문의 만료 처리(15분 예약 TTL + 최대 5분 폴링)를 끝낼 시간을 준다. 여기서
     * 곧바로 clear() 하면, 뒤늦게 도착한 만료 배치의 rollbackStock이 이미 사라진 키를 보고
     * STOCK_NOT_INITIALIZED로 실패해 주문이 영원히 PENDING에 남는다
     * (DropStockFinalizeThenRollbackBugTest 참고).
     *
     * <p>정확히 1회만 실행되도록 markStockFinalized로 먼저 게이트를 건다 — 인스턴스가 여러 대면
     * 각자의 tryMarkEnded() 캐시 플래그(JVM 로컬)를 통과해 finalizeStock이 중복 호출될 수 있다.
     *
     * <p>확정 값은 Redis peek()이 아니라 항상 drop_entry 합계로 계산한다. peek()을 쓰면 여러
     * 인스턴스가 서로 다른 시점의 Redis 스냅샷을 최종값으로 덮어쓸 수 있어(마지막 쓰기가 이긴다),
     * 게이트가 뚫려도 결과가 같도록 계산을 결정적으로(하나의 진실 공급원으로) 만든다.
     */
    @Transactional
    public void finalizeStock(Drop drop) {
        if (!dropService.markStockFinalized(drop.getId())) {
            log.debug("드롭 재고가 이미 확정돼 있어 건너뜁니다. dropId={}", drop.getId());
            return;
        }

        int finalRemain = calculateRemain(drop.getId(), drop.getProductId());

        productPort.syncRemainQuantity(drop.getProductId(), finalRemain);
        stockReservationPort.clear(drop.getId());

        log.info("드롭 재고 확정. dropId={}, remain={}", drop.getId(), finalRemain);
    }

    private int calculateRemain(Long dropId, Long productId) {
        int total = productPort.getTotalQuantity(productId);
        int reserved = dropEntryRepository.sumReservedQuantity(dropId);

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
        int expected = calculateRemain(drop.dropId(), drop.productId());

        if (expected != redisRemain) {
            // dropId는 계속 늘어나는 값이라 metric label로 쓰지 않는다. 발생 빈도만 집계하고
            // 어느 드롭인지는 로그에서 찾는다.
            meterRegistry.counter("openbake.drop.stock.drift").increment();
            log.warn("재고 카운터와 drop_entry 합계가 다릅니다. dropId={}, redis={}, drop_entry기준={}",
                    drop.dropId(), redisRemain, expected);
        }
    }
}