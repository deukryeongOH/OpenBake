package com.openbake.order.application;

import com.openbake.common.exception.BusinessException;
import com.openbake.common.exception.ErrorCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 주문 생성 요청. <b>세 경로가 한 커맨드에 들어오지만 타입으로 분기하지 않는다</b> —
 * 진입점이 이미 갈려 있어서 어떤 값을 보냈는지가 곧 경로다.
 *
 * 가격은 받지 않는다. 클라이언트가 보내는 것은 "무엇을 살지"(의도)까지고,
 * "몇 개·언제·얼마"(사실)는 서버가 읽는다. 가격을 받으면 금액을 위조할 수 있다.
 *
 * 드롭은 수량도 받지 않는다. 선점 시 drop 이 저장해 둔 값을 서버가 읽는다.
 */
public record OrderCreateCommand(
        //경로 1 — 장바구니에서 고른 항목들.
        List<Long> cartItemIds,
        //경로 2 — 상품 상세에서 바로 주문.
        Long productId,
        Integer quantity,
        //경로 3 — 드롭. lock-start 로 선점을 마친 뒤에 온다.
        Long dropId,
        //경로 2·3 공통. 경로 1 은 장바구니 항목마다 픽업일이 이미 붙어 있다.
        LocalDate pickUpDate
) {

    public OrderRoute route() {
        boolean hasCart = cartItemIds != null && !cartItemIds.isEmpty();
        boolean hasProduct = productId != null;
        boolean hasDrop = dropId != null;

        //둘 이상을 동시에 보내면 어느 경로인지 서버가 정할 수 없다. 고르는 것은 클라이언트 몫이다.
        int given = (hasCart ? 1 : 0) + (hasProduct ? 1 : 0) + (hasDrop ? 1 : 0);
        if (given != 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "cartItemIds / productId / dropId 중 하나만 보내야 합니다.");
        }

        if (hasCart) {
            return OrderRoute.CART;
        }
        if (hasProduct) {
            if (quantity == null || quantity <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "수량은 1 이상이어야 합니다.");
            }
            return OrderRoute.DIRECT_GENERAL;
        }
        return OrderRoute.DROP;
    }

    public enum OrderRoute {
        CART, DIRECT_GENERAL, DROP
    }
}
