package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
//import com.openbake.drop.application.dto.DropQuantityReservedEvent;
import com.openbake.drop.application.queue.DropQueue;
import com.openbake.drop.application.queue.InMemoryQueueManager;
import com.openbake.drop.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;




@Service
@RequiredArgsConstructor
@Slf4j
public class DropLockService {

    private final DropRepository dropRepository;
    private final DropService dropService;
    private final DropInventoryRepository dropInventoryRepository;
    private final DropEntryRepository dropEntryRepository;
    private final InMemoryQueueManager queueManager;
//    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void decreaseQuantity(Long dropId, Long memberId, int quantity) {
        DropEntry dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEVER_ENTERED));

        if (dropEntry.getEntryStatus() != EntryStatus.ENTERED) {
            throw new BusinessException(ErrorCode.NOT_ENTERED_STATUS);
        }

        DropInventory dropInventory = dropInventoryRepository.findByDropId(dropId);
        dropInventory.decreaseQuantity(quantity);

        if (dropInventory.getRemainQuantity() == 0) {
            dropService.changeDropStatusCompleted(dropInventory.getDropId());
            queueManager.markSoldOut(dropId);
        }

        dropEntry.reserveEntry();

    }

    @Transactional
    public void rollbackStock(Long dropId, Long memberId, int quantity){
        DropInventory dropInventory = dropInventoryRepository.findByDropId(dropId);
        dropInventory.increaseStock(quantity);

        DropEntry dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEVER_ENTERED));

        dropEntry.failEntry(); // entryStatus를 fail로 바꿈.
    }

    public void checkEntryStatus(Long dropId, Long memberId) {
        DropEntry dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEVER_ENTERED));

        if (dropEntry.getEntryStatus() != EntryStatus.ENTERED) {
            throw new BusinessException(ErrorCode.NOT_ENTERED_STATUS);
        }
    }


    public void checkLimitQuantityPerPerson(Long dropId, int quantity){
        Drop drop = dropRepository.findById(dropId).orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));

        if (drop.getLimitQuantity() < quantity) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY_LIMIT_PER_PERSON);
        }
    }



//    public void confirmEventPublisher(Long dropId, Long memberId, int quantity) {
//        DropQuantityReservedEvent event = DropQuantityReservedEvent.of(dropId, memberId, quantity);
//        eventPublisher.publishEvent(event);
//
//        log.info("DropLockService 재고 선점 및 이벤트 발행 완료 dropId: {}, memberId: {}, quantity: {}", dropId, memberId, quantity);
//    }
}
