package com.openbake.order.infrastructure.client;

import com.openbake.order.application.port.SettlementPort;
import com.openbake.order.application.port.dto.PurchaseConfirmedInfo;
import com.openbake.settlement.application.event.PurchaseConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * SettlementPort 구현체. 구매확정 사실을 정산이 아는 형태로 바꿔 발행한다.
 *
 * 정산 리스너가 AFTER_COMMIT 이라, 확정이 롤백되면 정산은 실행되지 않고
 * 커밋된 뒤에야 별도 트랜잭션에서 처리된다.
 *
 * settlement 의 이벤트 타입은 이 파일 밖으로 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SettlementClient implements SettlementPort {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publishPurchaseConfirmed(PurchaseConfirmedInfo info) {
        eventPublisher.publishEvent(new PurchaseConfirmedEvent(
                info.eventId(),
                info.orderId(),
                info.orderItemId(),
                info.sellerId(),
                info.dropId(),
                info.productNameSnapshot(),
                info.quantity(),
                info.grossAmount(),
                info.purchaseConfirmedAt()
        ));
    }
}
