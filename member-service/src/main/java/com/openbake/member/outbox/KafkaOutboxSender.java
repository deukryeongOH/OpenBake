package com.openbake.member.outbox;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * OutboxEvent 한 건을 Kafka로 발행하고 broker ACK까지 기다린다.
 * 발행 자체와 OutboxEvent 상태 변경(PUBLISHED 처리)은 별도 관심사라 분리했다 — 이 클래스는 전송만 담당한다.
 */
@Component
@RequiredArgsConstructor
public class KafkaOutboxSender {

    private static final long ACK_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 전송 실패나 ACK 타임아웃 시 예외를 던진다 — 호출자가 재시도 여부를 판단한다. */
    public void send(OutboxEvent event) {
        try {
            kafkaTemplate
                    .send(event.getTopic(), event.getEventKey(), event.getPayload())
                    .get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Outbox 발행이 중단됨 eventId=" + event.getEventId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Outbox 발행 실패 eventId=" + event.getEventId(), e);
        }
    }
}
