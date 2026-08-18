package com.openbake.order.infrastructure.client;

import com.openbake.cart.domain.Cart;
import com.openbake.cart.domain.CartItem;
import com.openbake.cart.domain.CartRepository;
import com.openbake.order.application.port.CartPort;
import com.openbake.order.application.port.dto.CartInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * CartPort 구현체. cart 저장소를 직접 호출한다.
 * cart 의 타입(Cart, CartItem)은 이 파일 밖으로 나가지 않는다.
 *
 * TODO: 장바구니가 일반 상품 전용 1:N 으로 바뀌면서 이 어댑터의 계약이 맞지 않는다.
 *  - 장바구니에 dropId 가 없다. 드롭은 장바구니를 거치지 않고 바로 주문으로 간다.
 *  - 장바구니에 만료가 없다.
 *  - 항목이 여러 개다. CartInfo 는 단일 항목을 전제한다.
 *  order 개편(후속 이슈)에서 CartPort/CartInfo 를 다중 항목 기준으로 다시 설계한다.
 *  지금은 컴파일과 기존 시그니처 유지를 위해 첫 항목만 담아 내보낸다.
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
     * 장바구니 행 자체를 지운다.
     *
     * 장바구니는 더 이상 재고를 선점하지 않으므로 삭제해도 복구할 재고가 없다.
     */
    @Override
    public void deleteCart(Long memberId) {
        cartRepository.findByMemberId(memberId).ifPresent(cartRepository::delete);
    }

    //담긴 항목이 없는 장바구니가 있을 수 있어 quantity 는 0으로 내보낸다.
    private CartInfo toInfo(Cart cart) {
        CartItem item = cart.getItems().isEmpty() ? null : cart.getItems().getFirst();

        return new CartInfo(
                cart.getMemberId(),
                //장바구니는 일반 상품만 담는다. dropId 는 더 이상 존재하지 않는다.
                null,
                item == null ? 0 : item.getQuantity(),
                item == null ? null : item.getPickUpDate(),
                //만료 개념이 없어졌다. CartInfo.isExpired 가 항상 false 가 되게 한다.
                LocalDateTime.MAX
        );
    }
}
