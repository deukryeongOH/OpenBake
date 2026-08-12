package com.openbake.order.infrastructure.client;

import com.openbake.cart.domain.Cart;
import com.openbake.cart.domain.CartItem;
import com.openbake.cart.domain.CartRepository;
import com.openbake.order.application.port.CartPort;
import com.openbake.order.application.port.dto.CartInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * CartPort 구현체. cart 저장소를 직접 호출한다.
 * cart 의 타입(Cart, CartItem)은 이 파일 밖으로 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CartClient implements CartPort {

    private final CartRepository cartRepository;

    @Override
    public Optional<CartInfo> findCart(Long memberId) {
        return cartRepository.findByMemberId(memberId).map(this::toInfo);
    }

    /**
     * 재고를 복구하지 않고 장바구니만 지운다.
     *
     * CartService.deleteCart 는 삭제 전에 rollbackStock 을 호출하므로 여기서 쓰면 안 된다.
     * 주문이 성공한 뒤의 삭제는 선점을 주문이 확정한 것이라 재고가 돌아오면 안 되고,
     * 결제 실패 시의 복구는 OrderReservationReleaser 가 직접 요청한다.
     */
    @Override
    public void deleteCart(Long memberId) {
        cartRepository.findByMemberId(memberId).ifPresent(cartRepository::delete);
    }

    //담긴 항목이 없는 장바구니가 있을 수 있어 dropId/quantity 는 비워둔 채로 내보낸다.
    private CartInfo toInfo(Cart cart) {
        CartItem item = cart.getItems();

        return new CartInfo(
                cart.getMemberId(),
                item == null ? null : item.getDropId(),
                item == null ? 0 : item.getQuantity(),
                cart.getPickupDate(),
                cart.getExpiresAt()
        );
    }
}
