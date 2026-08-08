package com.openbake.cart.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CartRepository {
    //저장 즉시 반영해 carts.member_id UNIQUE 위반을 이 자리에서 확인한다.
    //위반 시 CART_ALREADY_EXISTS 로 올라온다(변환은 구현체 책임).
    Cart save(Cart cart);

    Optional<Cart> findByMemberId(Long memberId);

    //존재 여부만 필요할 때.
    boolean existsByMemberId(Long memberId);

    //만료 배치용. 재고를 복구하려면 dropId/quantity 를 알아야 하므로
    //벌크 삭제가 아니라 먼저 조회한다.
    //경계는 Cart.isExpired 와 같게 맞춘다(만료 시각 그 순간부터 만료).
    List<Cart> findAllByExpiresAtLessThanEqual(LocalDateTime now);

    void delete(Cart cart);

    //삭제를 즉시 확정한다. 같은 회원으로 새 장바구니를 만들기 전에 써야
    //뒤따르는 저장이 member_id UNIQUE 제약에 걸리지 않는다.
    void deleteImmediately(Cart cart);

    void deleteAll(List<Cart> carts);
}
