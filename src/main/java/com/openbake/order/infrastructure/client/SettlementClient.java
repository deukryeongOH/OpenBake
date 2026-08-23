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

    /**
     * ⚠️ 다섯 번째 인자는 settlement 의 PurchaseConfirmedEvent 에서 아직 이름이 dropId 다.
     *
     * settlement 담당자가 drop_id → product_id 로 바꾸기로 합의했고(settlement-dropid-issue.md),
     * 그 전환 전까지는 이름만 dropId 인 자리에 productId 가 들어간다.
     * <b>전환 전에는 일반 상품 구매확정이 정산에서 실패한다</b> — settlement 의 dropId 는
     * nullable=false + validatePositiveId 라 값 자체는 통과하지만, 드롭이 아닌 주문의
     * productId 가 drop_id 컬럼에 들어가는 형태가 되므로 전환 완료 전에는 E2E 를 돌리지 않는다.
     * 전환이 끝나면 이 주석과 함께 인자 이름이 자연히 맞아떨어진다.
     */
    @Override
    public void publishPurchaseConfirmed(PurchaseConfirmedInfo info) {
        eventPublisher.publishEvent(new PurchaseConfirmedEvent(
                info.eventId(),
                info.orderId(),
                info.orderItemId(),
                info.sellerId(),
                info.productId(),
                info.productNameSnapshot(),
                info.quantity(),
                info.grossAmount(),
                info.purchaseConfirmedAt()
        ));
    }
}
