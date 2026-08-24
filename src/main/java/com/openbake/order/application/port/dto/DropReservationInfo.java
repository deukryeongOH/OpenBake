package com.openbake.order.application.port.dto;

/**
 * 드롭 재고 선점 확인 결과.
 *
 * <b>수량을 클라이언트에서 받지 않는 이유가 이것이다.</b> 선점 시 drop 이
 * DropEntry.selectQuantity 에 저장해 둔 값을 그대로 읽어 쓴다.
 * 받지 않으니 대조할 대상도 없고, 수량 불일치라는 실패 자체가 생기지 않는다.
 */
public record DropReservationInfo(
        int selectQuantity,
        //선점(RESERVED) 상태인가. 이것이 곧 살 자격이다.
        boolean reserved
) {
}
