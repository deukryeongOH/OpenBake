package com.openbake.interaction.infrastructure;

import com.openbake.common.event.EventTopics;
import com.openbake.common.event.InteractionType;
import com.openbake.common.event.MemberInteractionEvent;
import com.openbake.interaction.application.ProductViewedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductViewedKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ProductViewedEvent source) {
        MemberInteractionEvent event = MemberInteractionEvent.create(
                InteractionType.VIEW, source.memberId(), source.productId(), source.dropId(),
                1, null, source.occurredAt());
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(
                            EventTopics.PRODUCT_VIEWED,
                            source.memberId().toString(),
                            payload)
                    .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(event, exception);
        } catch (Exception exception) {
            recordFailure(event, exception);
        }
    }

    private void recordFailure(MemberInteractionEvent event, Exception exception) {
        meterRegistry.counter("openbake.interaction.publish.failures", "topic", EventTopics.PRODUCT_VIEWED)
                .increment();
        log.warn("VIEW Kafka 발행 실패 eventId={} memberId={} productId={}",
                event.eventId(), event.memberId(), event.productId(), exception);
    }
}
