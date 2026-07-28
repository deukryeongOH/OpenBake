package com.openbake.drop.application.event;

import com.openbake.drop.application.dto.OrderCanceledEvent;
import com.openbake.drop.application.dto.PaymentCompletedEvent;
import com.openbake.drop.application.dto.PaymentFailedEvent;
import com.openbake.drop.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class DropEventListener {

    private final DropInventoryRepository dropInventoryRepository;
    private final DropEntryRepository dropEntryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event){
        // 결제 완료 시 드롭 대기열 재입장 불가 (entryStatus Completed로 변경)
        // 여기서는 dropEntry에 저장되어있는 entryStatus를 Completed로 변경해야함

        Optional<DropEntry> dropEntry = dropEntryRepository.findByDropIdAndMemberId(event.dropId(), event.memberId());
        dropEntry.ifPresent(entry -> {
            if (entry.getEntryStatus() != EntryStatus.RESERVED) {
                return ;
            }
            entry.completePayment();
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event){
        // 결제 실패 시 entry 상태 값 변경 후 재고 복구
        changeEntryStatusAndRecoverStock(event.dropId(), event.memberId(), event.quantity(), DropEntry::failPayment);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCanceled(OrderCanceledEvent event){
        // 주문 취소 시 entry 상태 값 변경 후 재고 복구
        changeEntryStatusAndRecoverStock(event.dropId(), event.memberId(), event.quantity(), DropEntry::cancelEntry);
    }

    private void changeEntryStatusAndRecoverStock(Long dropId, Long memberId, int quantity, Consumer<DropEntry> transition){
        Optional<DropEntry> dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId);
        dropEntry.ifPresent(entry -> {
            if (entry.getEntryStatus() != EntryStatus.RESERVED)
                return;

            transition.accept(entry);
            dropInventoryRepository.findByDropId(entry.getDropId()).increaseStock(quantity);
        });
    }
}
