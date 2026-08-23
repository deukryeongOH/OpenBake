package com.openbake.order.application.port;

import com.openbake.order.application.port.dto.DropReservationInfo;

public interface ReservationPort {

    /**
     * 선점 확인. 드롭 주문 생성의 유일한 자격 검사다.
     *
     * 드롭 마감 여부는 보지 않는다 — 선점이 곧 살 자격이고, 주문서를 쓰는 동안
     * dropEnd 가 지났다고 이미 잡은 재고를 뺏으면 안 된다.
     */
    DropReservationInfo getReservation(Long dropId, Long memberId);

    /**
     * 드롭 재고 복구.
     *
     * <b>수량을 넘기지 않는다.</b> drop 이 DropEntry.selectQuantity 에 저장해 둔 값을
     * 읽어 되돌린다 — 수량을 아는 책임은 drop 에 있다.
     * 되돌리는 재고 행 자체는 일반 상품과 같은 product_inventories 다.
     */
    void rollbackStock(Long dropId, Long memberId);
}
