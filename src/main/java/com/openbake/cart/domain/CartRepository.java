package com.openbake.cart.domain;

import java.util.Optional;

public interface CartRepository {
    //저장 즉시 반영해 carts.member_id UNIQUE 위반을 이 자리에서 확인한다.
    //위반 시 CART_ALREADY_EXISTS 로 올라온다(변환은 구현체 책임).
    Cart save(Cart cart);

    Optional<Cart> findByMemberId(Long memberId);

    //존재 여부만 필요할 때.
    boolean existsByMemberId(Long memberId);

    //장바구니 행 자체를 지운다. 사용자 흐름에서는 아이템만 지우므로 여기서 쓰지 않고,
    //주문이 끝난 뒤 order 가 정리할 때 쓴다.
    void delete(Cart cart);
}
