package com.openbake.drop.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.queue.QueueManager;
import com.openbake.drop.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DropLockService {

    private final DropRepository dropRepository;
    private final DropService dropService;
    private final DropInventoryRepository dropInventoryRepository;
    private final DropEntryRepository dropEntryRepository;
    private final QueueManager queueManager;

    @Transactional
    public void decreaseQuantity(Long dropId, Long memberId, int selectQuantity) {
        DropEntry dropEntry = checkEntryStatus(dropId, memberId);

        DropInventory dropInventory = dropInventoryRepository.findByDropId(dropId);
        dropInventory.decreaseQuantity(selectQuantity);

        if (dropInventory.getRemainQuantity() == 0) {
            dropService.changeDropStatusCompleted(dropInventory.getDropId());
            queueManager.markSoldOut(dropId);
        }

        dropEntry.reserveEntryAndSaveSelectQuantity(selectQuantity);
    }

    @Transactional
    public void rollbackStock(Long dropId, Long memberId){
        DropEntry dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEVER_ENTERED));

        DropInventory dropInventory = dropInventoryRepository.findByDropId(dropId);
        dropInventory.increaseStock(dropEntry.getSelectQuantity());

        dropEntry.failEntry(); // entryStatus를 fail로 바꿈.

        reviveIfSoldOutCompleted(dropId, dropInventory);
    }

    @Transactional(readOnly = true)
    public int getSelectQuantity(Long dropId, Long memberId){
        DropEntry dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEVER_ENTERED));

        return dropEntry.getSelectQuantity();
    }

    private void reviveIfSoldOutCompleted(Long dropId, DropInventory dropInventory) {
        Drop drop = dropRepository.findById(dropId).orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));

        boolean stillWithinDropWindow = LocalDateTime.now().isBefore(drop.getDropEnd());
        if (drop.getDropStatus() == DropStatus.COMPLETED && stillWithinDropWindow && dropInventory.getRemainQuantity() > 0) {
            dropService.changeDropStatusActive(dropId);
            queueManager.unmarkSoldOut(dropId);
        }
    }

    public DropEntry checkEntryStatus(Long dropId, Long memberId) {
        DropEntry dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEVER_ENTERED));

        if (dropEntry.getEntryStatus() != EntryStatus.ENTERED) {
            throw new BusinessException(ErrorCode.NOT_ENTERED_STATUS);
        }
        return dropEntry;
    }


    public void checkLimitQuantityPerPerson(Long dropId, int quantity){
        Drop drop = dropRepository.findById(dropId).orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));

        if (drop.getLimitQuantity() < quantity) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY_LIMIT_PER_PERSON);
        }
    }


    public void checkSelectQuantity(Long dropId, int quantity) {
        DropInventory dropInventory = dropInventoryRepository.findByDropId(dropId);

        if (quantity > dropInventory.getRemainQuantity()) {
            throw new BusinessException(ErrorCode.INVALID_USER_SELECT_QUANTITY);
        }
    }
}
