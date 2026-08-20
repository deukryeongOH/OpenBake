package com.openbake.order.application;

import com.openbake.interaction.application.InteractionOutboxWriter;
import com.openbake.order.application.port.DropPort;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PurchaseConfirmedOutboxWriter {

    private final DropPort dropPort;
    private final InteractionOutboxWriter interactionOutboxWriter;

    public void write(Long memberId, Long dropId, int quantity, Long orderId, Instant occurredAt) {
        final Long productId;
        try {
            productId = dropPort.getProductId(dropId);
        } catch (RuntimeException exception) {
            log.warn("PURCHASE 행동 이벤트 생략: drop→product 변환 실패 orderId={} dropId={}",
                    orderId, dropId, exception);
            return;
        }
        interactionOutboxWriter.purchaseConfirmed(
                memberId, productId, dropId, quantity, orderId, occurredAt);
    }
}
