package com.openbake.order.infrastructure.client;

import com.openbake.cart.application.CartOrderItem;
import com.openbake.cart.application.CartService;
import com.openbake.order.application.port.CartPort;
import com.openbake.order.application.port.dto.CartItemInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CartPort 구현체. cart 가 아직 같은 코어 안에 있어 서비스를 직접 호출한다.
 *
 * 저장소가 아니라 서비스를 부른다. 소유권 검증(CA008)과 삭제의 멱등성은 cart 의
 * 규칙이라 order 가 저장소를 직접 뒤지면서 다시 구현할 일이 아니다.
 *
 * cart 의 타입(Cart, CartItem, CartOrderItem)은 이 파일 밖으로 나가지 않는다.
 */
//cart 자신에게도 같은 이름의 빈이 생길 수 있어 이름을 명시한다.
@Component("orderCartClient")
@RequiredArgsConstructor
public class CartClient implements CartPort {

    private final CartService cartService;

    @Override
    public List<CartItemInfo> findItemsForOrder(Long memberId, List<Long> cartItemIds) {
        return cartService.findItemsForOrder(memberId, cartItemIds).stream()
                .map(this::toInfo)
                .toList();
    }

    @Override
    public void removeItems(Long memberId, List<Long> cartItemIds) {
        cartService.removeItems(memberId, cartItemIds);
    }

    private CartItemInfo toInfo(CartOrderItem item) {
        return new CartItemInfo(
                item.cartItemId(),
                item.productId(),
                item.quantity(),
                item.pickUpDate()
        );
    }
}
