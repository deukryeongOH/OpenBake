package com.openbake.drop.application.service;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.dto.DropReserveCommand;
import com.openbake.drop.application.dto.SelectQuantityInfoResult;
import com.openbake.drop.application.port.CurrentMemberPort;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.queue.QueueManager;
import com.openbake.drop.domain.*;
import com.openbake.drop.domain.entity.Drop;
import com.openbake.drop.domain.entity.DropEntry;
import com.openbake.drop.domain.repository.DropEntryRepository;
import com.openbake.drop.domain.repository.DropRepository;
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
    private final DropEntryRepository dropEntryRepository;
    private final QueueManager queueManager;
    private final ProductPort productPort;
    private final CurrentMemberPort currentMemberPort;

    @Transactional
    public void reserveStock(Long dropId, DropReserveCommand command){
        Long memberId = currentMemberPort.getCurrentMemberId();
        // 1인당 제한 수량과 선택 수량 검증
        checkLimitQuantityPerPerson(dropId, command.quantity());

        decreaseQuantity(dropId, memberId, command.quantity());
    }

//    @Transactional 지금은 불필요 나중 분산락 적용해 DropLockFacade에서 호출 시 적용
    public void decreaseQuantity(Long dropId, Long memberId, int selectQuantity) {
        Drop drop = findDrop(dropId);
        if(dropEntryRepository.reserve(dropId, memberId, selectQuantity) == 0){
            throw new BusinessException(ErrorCode.NOT_ENTERED_STATUS);  // ENTERED 상태가 아님.
        } // 이 reserve를 실행하고 재고를 차감하기 전에 오류 발생해서 재고 차감을 못해도 한 트랜잭션 안에 있어 다 롤백 (but 추후에 FeignClient로 통신하게 되면 그땐 진짜 문제)

        if (productPort.decreaseQuantity(drop.getProductId(), selectQuantity) == 0) {
            dropService.changeDropStatusCompleted(dropId); // 위에서 dropEntryRepository.reserve 호출로 DB 값이 바뀌어 detached 상태라 drop을 바로 사용하지 않고 dropId를 통해 DB접근을 다시 해 Drop을 새로 받아 상태 변경을 하는게 맞음.
            queueManager.markSoldOut(dropId);
        }
    }

    @Transactional
    public void rollbackStock(Long dropId, Long memberId){
        DropEntry dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEVER_ENTERED));
        Drop drop = findDrop(dropId);

        if(dropEntryRepository.fail(dropId, memberId) == 0){
            throw new BusinessException(ErrorCode.NOT_RESERVED_STATUS); // 이미 롤백됐거나 RESERVED 상태가 아님.
        }

        int remainQuantity = productPort.rollbackQuantity(drop.getProductId(), dropEntry.getSelectQuantity());

        reviveIfSoldOutCompleted(dropId, remainQuantity);
    }

    @Transactional(readOnly = true) // 주문 쪽에서 사용자가 선택한 수량을 재검증 시 필요한 메서드
    public SelectQuantityInfoResult getSelectQuantity(Long dropId, Long memberId){
        DropEntry dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEVER_ENTERED));

        return SelectQuantityInfoResult.of(dropEntry.getSelectQuantity(), dropEntry.getEntryStatus());
    }

    private void reviveIfSoldOutCompleted(Long dropId, int remainQuantity) {
        Drop drop = findDrop(dropId);

        boolean stillWithinDropWindow = LocalDateTime.now().isBefore(drop.getDropEnd());
        if (drop.getDropStatus() == DropStatus.COMPLETED && stillWithinDropWindow && remainQuantity > 0) {
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
        Drop drop = findDrop(dropId);

        if (drop.getLimitQuantity() < quantity) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY_LIMIT_PER_PERSON);
        }
    }


    public void checkSelectQuantity(Long dropId, int quantity) {
        Drop drop = findDrop(dropId);
        DropProductInfoResult result = productPort.getProductInfo(drop.getProductId());

        if (quantity > result.remainQuantity()) {
            throw new BusinessException(ErrorCode.INVALID_USER_SELECT_QUANTITY);
        }
    }

    private Drop findDrop(Long dropId) {
        return dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));
    }
}
