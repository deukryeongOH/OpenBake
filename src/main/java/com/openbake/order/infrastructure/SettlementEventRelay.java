package com.openbake.order.infrastructure;

import com.openbake.order.domain.OrderOutboxEvent;
import com.openbake.order.domain.OrderOutboxEventRepository;
import com.openbake.order.domain.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 발신함 릴레이. PENDING 이벤트를 주기적으로 읽어 정산으로 전송한다.
 * 전송 성공 → SENT, 실패 → 재시도 횟수 기록 후 PENDING 유지(다음 주기 재시도),
 * 상한 초과 → FAILED. 정산이 eventId + (orderId,orderItemId)로 멱등 처리하므로
 * 중복 전송돼도 정산 대상이 이중 생성되지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementEventRelay {

    private static final int MAX_RETRY = 5;

    private final OrderOutboxEventRepository outboxRepository;
    private final SettlementClient settlementClient;

    //이전 실행이 끝난 뒤부터 세는 fixedDelay 라 실행이 겹치지 않는다.
    @Scheduled(fixedDelayString = "${openbake.outbox.relay-delay:PT5S}")
    @Transactional
    public void relay() {
        List<OrderOutboxEvent> pending =
                outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        for (OrderOutboxEvent event : pending) {
            try {
                settlementClient.sendPurchaseConfirmed(event.getPayload());
                event.markSent();
            } catch (Exception e) {
                //전송 실패. 재시도 횟수만 올리고 PENDING 유지 → 다음 주기에 재시도.
                event.recordFailure(e.getMessage());
                if (event.getRetryCount() >= MAX_RETRY) {
                    event.markFailed();
                    log.error("정산 이벤트 전송 최종 실패 eventId={}, reason={}", event.getEventId(), e.getMessage());
                } else {
                    log.warn("정산 이벤트 전송 실패, 재시도 예정 eventId={}, retry={}", event.getEventId(), event.getRetryCount());
                }
            }
        }
    }
}
