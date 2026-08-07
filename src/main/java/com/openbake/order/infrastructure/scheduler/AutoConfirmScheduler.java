package com.openbake.order.infrastructure.scheduler;

import com.openbake.order.application.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 자동 구매확정 배치. 결제 완료 후 지정 일수가 지나도록 확정되지 않은 주문을 자동 확정한다.
 * 판매자가 확정을 누르지 않아도 정산이 누락되지 않게 하는 안전망이다.
 *
 * 대상 조회와 확정을 분리해, 주문 하나가 실패해도 나머지는 계속 처리한다(주문별 트랜잭션).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoConfirmScheduler {

    private final OrderService orderService;

    //이전 실행이 끝난 뒤부터 세는 fixedDelay 라 실행이 겹치지 않는다.
    @Scheduled(fixedDelayString = "${openbake.order.auto-confirm-delay:PT1H}")
    public void run() {
        List<Long> targetIds = orderService.findAutoConfirmTargetIds();
        if (targetIds.isEmpty()) {
            return;
        }

        int confirmed = 0;
        for (Long orderId : targetIds) {
            try {
                orderService.autoConfirm(orderId);
                confirmed++;
            } catch (Exception e) {
                //한 건 실패가 배치 전체를 막지 않도록 로그만 남기고 계속 진행한다.
                log.error("자동 구매확정 실패 orderId={}, reason={}", orderId, e.getMessage());
            }
        }
        log.info("[배치] 자동 구매확정 완료 — 대상 {}건 중 {}건 확정", targetIds.size(), confirmed);
    }
}
