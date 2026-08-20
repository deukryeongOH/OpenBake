package com.openbake.drop.application.service;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.dto.DropReserveCommand;
import com.openbake.drop.application.dto.SelectQuantityInfoResult;
import com.openbake.drop.application.port.CurrentMemberPort;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.port.StockReservationPort;
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
    private final StockReservationPort stockReservationPort;

    @Transactional
    public void reserveStock(Long dropId, DropReserveCommand command){
        Long memberId = currentMemberPort.getCurrentMemberId();
        // 1인당 제한 수량과 선택 수량 검증
        checkLimitQuantityPerPerson(dropId, command.quantity());

        decreaseQuantity(dropId, memberId, command.quantity());
    }

    /**
     * 재고 선점.
     *
     * 실행 순서가 중요하다. drop_entry 를 먼저 갱신하고 Redis 차감을 트랜잭션 안에서 호출한다.
     * Redis 가 실패하면 예외를 던져 트랜잭션이 롤백되면서 drop_entry 도 원래 상태로 되돌아가므로
     * 별도의 보상 로직이 필요 없다. 순서를 뒤집으면 Redis 를 되돌리는 보상 호출이 필요해지고,
     * 그 보상마저 실패하면 재고가 영구히 누락된다.
     *
     * product_inventory 단일 row UPDATE 가 이 경로에서 완전히 빠지는 것이 이 변경의 핵심이다.
     */
//    @Transactional 지금은 불필요 나중 분산락 적용해 DropLockFacade에서 호출 시 적용
    public void decreaseQuantity(Long dropId, Long memberId, int selectQuantity) {
        Drop drop = findDrop(dropId);

        // 회원별 row 라 경합이 없다. 중복 선점은 WHERE entryStatus = 'ENTERED' 조건이 막는다.
        if(dropEntryRepository.reserve(dropId, memberId, selectQuantity) == 0){
            throw new BusinessException(ErrorCode.NOT_ENTERED_STATUS);  // ENTERED 상태가 아님.
        }

        long remain = stockReservationPort.reserve(dropId, selectQuantity);

        // 미초기화 상태에서 판매를 허용하면 초과 판매로 이어지므로 가용성보다 정합성을 택한다(fail-closed).
        if (remain == StockReservationPort.NOT_INITIALIZED) {
            throw new BusinessException(ErrorCode.STOCK_NOT_INITIALIZED);
        }
        if (remain == StockReservationPort.OUT_OF_STOCK) {
            throw new BusinessException(ErrorCode.DROP_OUT_OF_STOCK);
        }

        // 마지막 재고를 가져간 요청만 품절 처리한다. 매 요청이 아니라 드롭당 정확히 1회 실행된다.
        // 드롭 상품의 품절은 DropStatus.COMPLETED 로 표현한다. ProductStatus 는 일반 상품 전용 상태값이라
        // 여기서 건드리지 않는다(일반 상품 목록·검색 색인은 Type.GENERAL 만 대상으로 한다).
        if (remain == 0) {
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

        int totalQuantity = productPort.getTotalQuantity(drop.getProductId());
        long remainQuantity = stockReservationPort.rollback(dropId, dropEntry.getSelectQuantity(), totalQuantity);

        if (remainQuantity == StockReservationPort.NOT_INITIALIZED) {
            throw new BusinessException(ErrorCode.STOCK_NOT_INITIALIZED);
        }
        if (remainQuantity == StockReservationPort.OUT_OF_STOCK) {
            throw new BusinessException(ErrorCode.INVALID_TOTAL_QUANTITY); // 복구 후 총 수량을 넘는 비정상 롤백
        }

        reviveIfSoldOutCompleted(dropId, remainQuantity);
    }

    @Transactional(readOnly = true) // 주문 쪽에서 사용자가 선택한 수량을 재검증 시 필요한 메서드
    public SelectQuantityInfoResult getSelectQuantity(Long dropId, Long memberId){
        DropEntry dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEVER_ENTERED));

        return SelectQuantityInfoResult.of(dropEntry.getSelectQuantity(), dropEntry.getEntryStatus());
    }

    private void reviveIfSoldOutCompleted(Long dropId, long remainQuantity) {
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
