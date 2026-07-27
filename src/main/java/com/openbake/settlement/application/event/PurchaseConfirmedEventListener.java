package com.openbake.settlement.application.event;

import com.openbake.settlement.application.ReceivePurchaseConfirmedCommand;
import com.openbake.settlement.application.SettlementEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseConfirmedEventListener {

    private final SettlementEventService settlementEventService;

    /** REQUIRES_NEW : 구매확정 주문 트랜잭션이 커밋된 뒤 정산 저장을 별도의 트랜잭션으로 처리하기 위해서*/
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(PurchaseConfirmedEvent event) {

        settlementEventService.receive(
                new ReceivePurchaseConfirmedCommand(
                        event.eventId(),
                        event.orderId(),
                        event.orderItemId(),
                        event.sellerId(),
                        event.dropId(),
                        event.productNameSnapshot(),
                        event.quantity(),
                        event.grossAmount(),
                        event.purchaseConfirmedAt()
                )
        );

        log.info(
                "구매확정 정산 이벤트 처리 완료. eventId={}, orderId={}, orderItemId={}",
                event.eventId(),
                event.orderId(),
                event.orderItemId()
        );
    }
}