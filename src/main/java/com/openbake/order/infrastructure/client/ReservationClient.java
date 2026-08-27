package com.openbake.order.infrastructure.client;

import com.openbake.drop.application.dto.SelectQuantityInfoResult;
import com.openbake.drop.application.service.DropLockService;
import com.openbake.drop.domain.EntryStatus;
import com.openbake.order.application.port.ReservationPort;
import com.openbake.order.application.port.dto.DropReservationInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ReservationPort 구현체. drop 이 아직 같은 코어 안에 있어 서비스를 직접 호출한다.
 * drop 의 타입(SelectQuantityInfoResult, EntryStatus)은 이 파일 밖으로 나가지 않는다.
 */
//cart 에도 같은 이름의 어댑터가 있어 빈 이름을 명시한다(클래스 단순명이 겹치면 기동 실패).
@Component("orderReservationClient")
@RequiredArgsConstructor
public class ReservationClient implements ReservationPort {

    private final DropLockService dropLockService;

    /**
     * getSelectQuantity 는 주석에 "주문 쪽에서 사용자가 선택한 수량을 재검증 시 필요한
     * 메서드"라고 적힌, 정확히 이 용도로 만들어진 메서드다.
     *
     * 응모 이력이 아예 없으면 NEVER_ENTERED 가 올라온다. 그대로 흘려보낸다 —
     * 선점 없이 주문하려 한 것이므로 막는 것이 맞다.
     */
    @Override
    public DropReservationInfo getReservation(Long dropId, Long memberId) {
        SelectQuantityInfoResult result = dropLockService.getSelectQuantity(dropId, memberId);
        return new DropReservationInfo(
                result.selectQuantity(),
                result.status() == EntryStatus.RESERVED
        );
    }

    /**
     * 복구 수량은 넘기지 않는다. drop 이 DropEntry.selectQuantity 에 저장해 둔 값을 읽어 되돌린다.
     *
     * 이미 복구됐거나 RESERVED 가 아니면 NOT_RESERVED_STATUS 가 올라온다. 삼키지 않는다 —
     * 재고 복구가 실패했는데 취소가 성공한 것처럼 보이면 재고가 영구히 잠긴다.
     */
    @Override
    public void rollbackStock(Long dropId, Long memberId) {
        dropLockService.rollbackStock(dropId, memberId);
    }

    /**
     * 결제 성공 시 선점을 확정한다(RESERVED -> COMPLETED). docs/10 3.1절.
     *
     * completeReservation은 실패해도 예외를 던지지 않는다 — 결제 성공 트랜잭션
     * (OrderPayTransactions.decreaseStockAndMarkPaid) 안에서 호출되므로, 여기서
     * 예외가 올라가면 이미 끝난 결제까지 롤백된다.
     */
    @Override
    public void complete(Long dropId, Long memberId) {
        dropLockService.completeReservation(dropId, memberId);
    }
}
