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

    /**
     * 드롭 주문용. dropId 로 productId 를 되찾아 쓴다.
     *
     * 주문이 드롭 전용이던 시절의 진입점이다. 주문이 productId 를 들고 있지 않아
     * drop 에 물어봐야 했다.
     */
    public void write(Long memberId, Long dropId, int quantity, Long orderId, Instant occurredAt) {
        final Long productId;
        try {
            productId = dropPort.getProductId(dropId);
        } catch (RuntimeException exception) {
            log.warn("PURCHASE 행동 이벤트 생략: drop→product 변환 실패 orderId={} dropId={}",
                    orderId, dropId, exception);
            return;
        }
        write(memberId, productId, dropId, quantity, orderId, occurredAt);
    }

    /**
     * productId 를 이미 아는 경우. <b>일반 상품 주문이 여기로 온다.</b>
     *
     * 리팩터링으로 {@code OrderItem} 이 productId 를 직접 갖게 되어 drop 조회가 필요 없다.
     * 일반 상품은 dropId 가 null 이라 위 메서드로는 변환이 실패해 이벤트가 통째로 누락된다.
     * dropId 는 드롭 주문에서만 채워지고 일반 상품에서는 null 로 넘어간다.
     */
    public void write(Long memberId, Long productId, Long dropId,
                      int quantity, Long orderId, Instant occurredAt) {
        interactionOutboxWriter.purchaseConfirmed(
                memberId, productId, dropId, quantity, orderId, occurredAt);
    }
}
