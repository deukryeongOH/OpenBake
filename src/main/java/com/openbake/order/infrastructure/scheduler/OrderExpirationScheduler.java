package com.openbake.order.infrastructure.scheduler;

import com.openbake.order.application.OrderExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주문 만료 배치.
 *
 * 두 가지 일을 한다.
 * <ol>
 *   <li>만료 시각이 지난 PENDING 주문 정리 — <b>드롭 선점 재고를 되돌리는 유일한 안전망</b>이다</li>
 *   <li>슬롯 누수 청소 — 종료 상태인데 진행 중 주문 슬롯이 남은 행을 풀어 준다</li>
 * </ol>
 *
 * 2번이 없으면 전이 한 군데만 빠뜨려도 그 회원이 <b>영구히 주문을 못 한다.</b>
 * 1번의 15분 청소는 PENDING 만 보므로 이 경우를 구제하지 못한다.
 *
 * ⚠️ 이 배치가 커버하지 못하는 구간이 하나 남아 있다 — lock-start 로 선점만 해두고
 * 주문을 만들지 않은 경우다. 주문 행 자체가 없어 여기서는 보이지 않는다.
 * DropEntry(RESERVED)를 회수하는 배치는 drop 도메인 소관이고 아직 존재하지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationScheduler {

    private final OrderExpirationService expirationService;

    //이전 실행이 끝난 뒤부터 세는 fixedDelay 라 실행이 겹치지 않는다.
    @Scheduled(fixedDelayString = "${openbake.order.expiration-delay:PT5M}")
    public void run() {
        expirePendingOrders();
        releaseLeakedSlots();
    }

    private void expirePendingOrders() {
        List<Long> targetIds = expirationService.findExpiredPendingIds();
        if (targetIds.isEmpty()) {
            return;
        }

        int processed = 0;
        for (Long orderId : targetIds) {
            try {
                expirationService.expire(orderId);
                processed++;
            } catch (Exception e) {
                //한 건 실패가 배치 전체를 막지 않도록 로그만 남기고 계속 진행한다.
                log.error("주문 만료 처리 실패 orderId={}, reason={}", orderId, e.getMessage());
            }
        }
        log.info("[배치] 주문 만료 처리 — 대상 {}건 중 {}건 처리", targetIds.size(), processed);
    }

    private void releaseLeakedSlots() {
        try {
            int released = expirationService.releaseLeakedSlots();
            //0건이 정상이다. 0건이 아니면 상태 전이 어딘가에서 슬롯 반납을 빠뜨렸다는 뜻이다.
            if (released > 0) {
                log.error("[배치] 진행 중 주문 슬롯 누수 {}건을 반납했다 — 상태 전이 경로를 점검해야 한다", released);
            }
        } catch (Exception e) {
            log.error("슬롯 누수 청소 실패 reason={}", e.getMessage());
        }
    }
}
