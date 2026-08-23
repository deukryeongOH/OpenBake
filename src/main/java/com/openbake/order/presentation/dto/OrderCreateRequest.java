package com.openbake.order.presentation.dto;

import com.openbake.order.application.OrderCreateCommand;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 주문 생성 요청. 경로 셋을 한 본문이 겸한다 — <b>어떤 값을 보냈는지가 곧 경로다.</b>
 *
 * <pre>
 * cartItemIds        장바구니에서 고른 항목으로 주문
 * productId+quantity 상품 상세에서 바로 주문
 * dropId             드롭 주문(lock-start 로 선점을 마친 뒤)
 * </pre>
 *
 * 셋 중 <b>하나만</b> 보내야 한다. 둘 이상이면 어느 경로인지 서버가 정할 수 없다.
 *
 * <b>가격은 받지 않는다.</b> 받으면 금액을 위조할 수 있다.
 * 드롭은 수량도 받지 않는다 — 선점 시 저장된 값을 서버가 읽는다.
 * 약관 동의는 여기가 아니라 결제 요청에 있다. 이 시점에는 아직 동의 전이다.
 */
public record OrderCreateRequest(

        @Schema(description = "주문할 장바구니 항목 ID 목록. 장바구니 경로에서만 보낸다.", example = "[12, 15]")
        List<Long> cartItemIds,

        @Schema(description = "상품 ID. 바로 주문 경로에서만 보낸다.", example = "7")
        Long productId,

        @Schema(description = "수량. 바로 주문 경로에서만 보낸다. 드롭은 서버가 선점 수량을 읽는다.", example = "2")
        Integer quantity,

        @Schema(description = "드롭 ID. 드롭 경로에서만 보낸다. lock-start 로 선점을 마친 뒤여야 한다.", example = "3")
        Long dropId,

        @Schema(description = "픽업 날짜. 바로 주문·드롭 경로에서 보낸다. 장바구니 경로는 항목마다 이미 붙어 있다.",
                example = "2026-08-25")
        LocalDate pickUpDate
) {

    public OrderCreateCommand toCommand() {
        return new OrderCreateCommand(cartItemIds, productId, quantity, dropId, pickUpDate);
    }
}
