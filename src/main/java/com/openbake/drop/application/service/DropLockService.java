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
import java.util.List;

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
    // reserveStock의 @Transactional 안에서만 호출되므로 여기 별도로 걸 필요는 없다.
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

    /**
     * 결제 성공 시 선점을 확정한다(RESERVED -> COMPLETED). docs/10 3.1절 1단계.
     *
     * order 쪽 결제 성공 트랜잭션(OrderPayTransactions.decreaseStockAndMarkPaid) 안에서
     * 호출된다. 그래서 <b>여기서는 예외를 던지지 않는다</b> — 예외가 나가면 이미 끝난
     * 결제(markPaid)까지 롤백된다. 0건이면 경합으로 이미 확정됐거나(같은 결제 결과 재전송)
     * 이미 롤백된 드문 경우로 보고 로그만 남긴다.
     */
    @Transactional
    public void completeReservation(Long dropId, Long memberId) {
        if (dropEntryRepository.complete(dropId, memberId) == 0) {
            log.warn("선점 확정 대상이 없습니다(이미 확정됐거나 RESERVED 상태가 아님). dropId={}, memberId={}",
                    dropId, memberId);
        }
    }

    /**
     * 방치된 선점 후보 조회. DropScheduler.sweepAbandonedReservations가 호출한다(docs/10 3.2절).
     * 실제 회수는 이 목록을 순회하며 rollbackStock을 호출하는 스케줄러 쪽 책임이다 —
     * rollbackStock이 이미 "조건부 UPDATE 후 성공한 것만 Redis 롤백"을 하고 있어 재사용한다.
     */
    @Transactional(readOnly = true)
    public List<DropEntry> findExpiredReservations(LocalDateTime cutoff) {
        return dropEntryRepository.findExpiredReservations(cutoff);
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
     * 재고 선점의 hot path라 정상 상황에서는 캐시만 읽는다.
     *
     * limitQuantity는 드롭 시작 후 불변이므로 캐시 값이 항상 정확하다. 다만 캐시 미스가
     * "오늘 드롭이 아님"만을 뜻하지는 않는다 — 당일 등록/수정 무효화 신호가 아직 이 Pod에
     * 전파되지 않은 경우도 캐시 미스로 나타난다(docs/11-drop-cache-invalidation-propagation.md).
     * 후자를 fail-closed로 거부하면 재고가 있는데도 거부당하므로, resolveProductId와 같은
     * 패턴으로 DB에 한 번 더 물어보고 있으면 그 자리에서 캐시를 재갱신한다.
     */
    public void checkLimitQuantityPerPerson(Long dropId, int quantity){
        int limitQuantity = todayDropCache.find(dropId)
                .map(CachedDrop::limitQuantity)
                .orElseGet(() -> resolveLimitQuantityAndRefresh(dropId));

        if (limitQuantity < quantity) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY_LIMIT_PER_PERSON);
        }
    }

    private int resolveLimitQuantityAndRefresh(Long dropId) {
        int limitQuantity = findDrop(dropId).getLimitQuantity();
        // 존재하는 드롭이면 이 Pod의 캐시가 뒤처져 있었다는 뜻이므로 즉시 재갱신해
        // 다음 요청부터는 다시 캐시로 처리되게 한다.
        todayDropCache.refresh();
        return limitQuantity;
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
