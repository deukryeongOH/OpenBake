package com.openbake.drop.infrastructure.scheduler;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.service.DropEnterService;
import com.openbake.drop.application.service.DropLockService;
import com.openbake.drop.application.service.DropService;
import com.openbake.drop.application.service.DropStockSyncService;
import com.openbake.drop.application.cache.TodayDropCache;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.entity.DropEntry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DropScheduler {

    private final DropService dropService;
    private final DropEnterService dropEnterService;
    private final TodayDropCache todayDropCache;
    private final DropStockSyncService dropStockSyncService;
    private final DropLockService dropLockService;

    // 종료 직후 곧바로 확정하면, 아직 진행 중인 주문 만료 처리(15분 예약 TTL + 최대 5분 폴링)가
    // 끝나기 전에 Redis 키가 사라져 뒤늦은 rollbackStock이 실패한다. 20분이 이론상 하한이지만
    // 스케줄러 지연·주문 생성 지연의 여유를 더해 30분으로 둔다.
    @Value("${openbake.drop.stock-finalize-delay:PT30M}")
    private Duration stockFinalizeDelay;

    // "선점 후 결제 완료까지 허용할 시간". order.reservation-ttl(15분)과 값을 맞췄다 —
    // 주문서 자체의 유효시간과 다르게 둘 이유가 없다(docs/10 3.2절). 다만 order 설정을
    // 직접 참조하지는 않는다 — drop이 order의 설정 키에 종속되면 안 된다.
    @Value("${openbake.drop.reservation-ttl:15m}")
    private Duration reservationTtl;

    // 서버 기동 시 당일 드롭 정보를 1회 캐싱 (자정 스케줄을 못 탄 채로 기동될 수 있으므로)
    @PostConstruct
    void init() {
        todayDropCache.refresh();
    }

    // 매일 자정에 1회만 DB를 조회해 오늘의 드롭 정보를 캐싱
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshTodayDrop() {
        todayDropCache.refresh();
    }

    // 200ms마다 실행되지만 DB에는 접근하지 않고, 캐싱된 시간 정보로만 진행 중 여부를 판단한다.
    // 상태 전환은 tryMarkStarted/tryMarkEnded가 CAS로 막아 드롭당 정확히 1회만 일어난다.
    @Scheduled(fixedRate = 200)
    public void processDropLifecycle() {
        LocalDateTime now = LocalDateTime.now();
        for(CachedDrop drop : todayDropCache.get()){
            if (now.isAfter(drop.dropEnd())) {
                if (drop.tryMarkEnded()) {
                    dropService.changeDropStatusCompleted(drop.dropId());
                    // 입장만 하고 구매로 이어지지 않은 진입 내역을 마감 시점에 한 번만 정리한다.
                    dropEnterService.expireRemainingEntries(drop.dropId());
                    // 재고 최종 확정(finalizeStock)은 여기서 곧바로 하지 않는다. finalizeStockAfterGracePeriod 참고.
                }
                continue;
            }

            if (now.isBefore(drop.dropStart())) {
                continue;
            }

            if (drop.tryMarkStarted()) {
                dropService.changeDropStatusActive(drop.dropId());
                // 재고 카운터가 없으면 요청이 전부 fail-closed 로 거부되므로 ACTIVE 전환과 같은 시점에 워밍업한다.
                dropStockSyncService.warmUp(drop);
            }
        }
    }

    // 진행 중인 드롭의 Redis 재고를 DB에 반영한다. 드롭당 UPDATE 1회라 경합이 없다.
    // 화면에 보이는 잔여 수량의 신선도를 결정하므로 짧은 주기를 유지한다.
    @Scheduled(fixedRate = 2000)
    public void syncDropStock() {
        LocalDateTime now = LocalDateTime.now();
        for (CachedDrop drop : todayDropCache.get()) {
            if (now.isBefore(drop.dropStart()) || now.isAfter(drop.dropEnd())) {
                continue;
            }

            dropStockSyncService.sync(drop);
        }
    }

    // 재고 카운터와 drop_entry 합계의 대조. 로그만 남기는 관측 로직이고
    // 집계 스캔이라 참여자가 늘수록 무거워지므로, 동기화보다 훨씬 긴 주기로 돈다.
    @Scheduled(fixedRate = 30000)
    public void checkDropStockDrift() {
        LocalDateTime now = LocalDateTime.now();
        for (CachedDrop drop : todayDropCache.get()) {
            if (now.isBefore(drop.dropStart()) || now.isAfter(drop.dropEnd())) {
                continue;
            }

            dropStockSyncService.checkDrift(drop);
        }
    }

    /**
     * 드롭 종료로부터 stockFinalizeDelay가 지난 뒤에야 재고를 최종 확정한다.
     *
     * TodayDropCache가 아니라 DB(findStockFinalizationCandidates)를 기준으로 돈다 — 23시대
     * 드롭은 확정 시점이 자정을 넘겨 캐시에서 이미 빠진 뒤일 수 있고, 재기동으로 놓친 것도
     * 다음 tick에 DB 조회로 자동 복구되게 하려는 목적이다.
     *
     * 유예 시간을 두는 이유와 정확히 1회만 실행되는 이유는 DropStockSyncService.finalizeStock
     * 참고. 5분 주기면 이 목적에 충분하고, order.expiration-delay(5분)와 같은 자릿수로 맞췄다.
     */
    @Scheduled(fixedDelayString = "${openbake.drop.stock-finalize-interval:PT5M}")
    public void finalizeStockAfterGracePeriod() {
        LocalDateTime cutoff = LocalDateTime.now().minus(stockFinalizeDelay);
        List<Drop> candidates = dropService.findStockFinalizationCandidates(cutoff);

        for (Drop drop : candidates) {
            try {
                dropStockSyncService.finalizeStock(drop);
            } catch (Exception e) {
                // 한 건 실패가 배치 전체를 막지 않도록 로그만 남기고 계속 진행한다.
                log.error("드롭 재고 확정 실패 dropId={}, reason={}", drop.getId(), e.getMessage());
            }
        }
    }

    /**
     * 선점(lock-start)만 하고 결제로 이어지지 않은 채 reservationTtl이 지난 항목을 회수한다
     * (docs/10 3.2절 — "선점만 하고 이탈하면 마감까지 재고가 잠긴다"의 근본 해결).
     *
     * 1단계(주문 성공 시 RESERVED -> COMPLETED 확정)가 먼저 있어야 안전하다 — 그게 없으면
     * 정상 결제된 주문도 "방치된 선점"으로 오판해 재고를 회수하게 된다. 1단계가 끝난 지금은
     * COMPLETED로 전이된 결제 완료 건이 이 조회(entryStatus = 'RESERVED')에 애초에 잡히지
     * 않으므로 안전하다.
     *
     * 회수 자체는 새로 만들지 않고 rollbackStock을 그대로 재사용한다 — "조건부 UPDATE로
     * 먼저 선점한 뒤, 성공한 행에 대해서만 Redis 롤백 + 품절 복구"가 이미 그 메서드의 동작이다.
     * 인스턴스가 여러 대라 같은 후보를 동시에 집어도, fail() 쿼리의 조건부 UPDATE가 한쪽만
     * 통과시키므로 진 쪽은 NOT_RESERVED_STATUS를 받는다 — 이건 버그가 아니라 정상적인 경합
     * 처리 결과라 별도로 구분해 조용히 넘어간다.
     */
    @Scheduled(fixedDelayString = "${openbake.drop.reservation-sweep-interval:PT5M}")
    public void sweepAbandonedReservations() {
        LocalDateTime cutoff = LocalDateTime.now().minus(reservationTtl);
        List<DropEntry> candidates = dropLockService.findExpiredReservations(cutoff);

        for (DropEntry entry : candidates) {
            try {
                dropLockService.rollbackStock(entry.getDropId(), entry.getMemberId());
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.NOT_RESERVED_STATUS) {
                    // 조회와 회수 사이에 결제가 완료됐거나 다른 인스턴스가 먼저 회수했다 —
                    // 조건부 UPDATE가 막아준 정상적인 경합이다.
                    log.debug("이미 처리된 선점이라 건너뜁니다(경합 또는 결제 완료). dropId={}, memberId={}",
                            entry.getDropId(), entry.getMemberId());
                } else {
                    log.error("방치된 선점 회수 실패 dropId={}, memberId={}, reason={}",
                            entry.getDropId(), entry.getMemberId(), e.getMessage());
                }
            } catch (Exception e) {
                log.error("방치된 선점 회수 실패 dropId={}, memberId={}, reason={}",
                        entry.getDropId(), entry.getMemberId(), e.getMessage());
            }
        }
    }
}