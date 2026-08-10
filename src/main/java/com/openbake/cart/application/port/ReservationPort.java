package com.openbake.cart.application.port;

import com.openbake.cart.application.port.dto.ReservationInfo;

import java.util.Optional;

/**
 * 재고 선점 조회/복구 포트.
 *
 * 재고를 차감하는 책임은 drop 에 있다. cart 는 선점 여부를 확인하고,
 * 이탈 시 복구를 요청하기만 한다.
 */
public interface ReservationPort {

    //응모 이력이 없으면 빈 값.
    Optional<ReservationInfo> findReservation(Long dropId, Long memberId);

    //복구 수량은 넘기지 않는다. drop 이 선점 시 저장해 둔 수량을 읽어 되돌린다.
    void rollbackStock(Long dropId, Long memberId);
}
