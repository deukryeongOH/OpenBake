package com.openbake.drop.application.service;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;
import com.openbake.drop.application.cache.CachedDrop;
import com.openbake.drop.application.cache.TodayDropCache;
import com.openbake.drop.application.dto.DropProductInfoResult;
import com.openbake.drop.application.dto.DropReserveCommand;
import com.openbake.drop.application.dto.SelectQuantityInfoResult;
import com.openbake.drop.application.port.CurrentMemberPort;
import com.openbake.drop.application.port.ProductPort;
import com.openbake.drop.application.port.StockReservationPort;
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
    private final ProductPort productPort;
    private final CurrentMemberPort currentMemberPort;
    private final StockReservationPort stockReservationPort;
    private final TodayDropCache todayDropCache;

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
        }
    }

    @Transactional
    public void rollbackStock(Long dropId, Long memberId){
        DropEntry dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEVER_ENTERED));

        if(dropEntryRepository.fail(dropId, memberId) == 0){
            throw new BusinessException(ErrorCode.NOT_RESERVED_STATUS); // 이미 롤백됐거나 RESERVED 상태가 아님.
        }

        int totalQuantity = productPort.getTotalQuantity(resolveProductId(dropId)); // resolveProductId -> 캐시 조회
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

    /**
     * 품절로 COMPLETED가 된 드롭을 롤백으로 재고가 생겼을 때 되살린다.
     *
     * 값싼 판정을 앞에 세워 DB 접근을 걸러낸다. 캐시에 없으면 오늘 드롭이 아니므로 진행 창 밖인 게 확정이고,
     * 창 밖이거나 복구된 재고가 없으면 되살릴 일 자체가 없다.
     *
     * 마지막 단계도 상태를 읽지 않는다. "COMPLETED일 때만 ACTIVE로"라는 조건을 UPDATE의 WHERE에 넣어
     * SELECT 없이 한 번에 처리한다. 읽고 나서 쓰는 사이에 상태가 바뀔 여지도 함께 사라진다.
     */
    private void reviveIfSoldOutCompleted(Long dropId, long remainQuantity) {
        if (remainQuantity <= 0) {
            return;
        }

        boolean stillWithinDropWindow = todayDropCache.find(dropId)
                .map(drop -> LocalDateTime.now().isBefore(drop.dropEnd()))
                .orElse(false);
        if (!stillWithinDropWindow) {
            return;
        }
        // 복구 후보일 때만 Active로 변경
        dropService.reviveFromSoldOut(dropId);
    }

    public DropEntry checkEntryStatus(Long dropId, Long memberId) {
        DropEntry dropEntry = dropEntryRepository.findByDropIdAndMemberId(dropId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEVER_ENTERED));

        if (dropEntry.getEntryStatus() != EntryStatus.ENTERED) {
            throw new BusinessException(ErrorCode.NOT_ENTERED_STATUS);
        }
        return dropEntry;
    }


    /**
     * 재고 선점의 hot path라 DB를 타지 않는다.
     *
     * limitQuantity는 드롭 시작 후 불변이므로 캐시 값이 항상 정확하다.
     * 캐시에 없다는 건 오늘 진행되는 드롭이 아니라는 뜻이고, 재고 선점은 진행 중인 드롭에서만 유효하므로
     * DB로 되묻지 않고 거부한다(fail-closed). 재고 경로가 이미 택한 정책과 같은 방향이다.
     */
    public void checkLimitQuantityPerPerson(Long dropId, int quantity){
        CachedDrop drop = todayDropCache.find(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_ACTIVE));

        if (drop.limitQuantity() < quantity) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY_LIMIT_PER_PERSON);
        }
    }


    public void checkSelectQuantity(Long dropId, int quantity) {
        DropProductInfoResult result = productPort.getProductInfo(resolveProductId(dropId));

        if (quantity > result.remainQuantity()) {
            throw new BusinessException(ErrorCode.INVALID_USER_SELECT_QUANTITY);
        }
    }

    /**
     * productId도 드롭 시작 후 불변이라 캐시로 해결되지만, 여기는 fail-closed를 쓸 수 없어 DB 폴백을 둔다.
     * 롤백(주문 취소/결제 실패 보상)은 드롭 당일이 지난 뒤에도 들어올 수 있는데,
     * 그때는 캐시(오늘 드롭만 보관)가 비어 있다. 캐시 미스로 거부해버리면 정상 취소가 실패하면서
     * 재고가 영구히 누락되므로, 이 경로만큼은 DB로 되물어야 한다.
     */
    private Long resolveProductId(Long dropId) {
        return todayDropCache.find(dropId)
                .map(CachedDrop::productId)
                .orElseGet(() -> findDrop(dropId).getProductId());
    }

    private Drop findDrop(Long dropId) {
        return dropRepository.findById(dropId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DROP_NOT_FOUND));
    }
}
