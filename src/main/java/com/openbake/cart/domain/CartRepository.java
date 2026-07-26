package com.openbake.cart.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByMemberId(Long memberId);
    boolean existsByMemberId(Long memberId);

    //만료 배치용. 재고를 복구하려면 dropId/quantity 를 알아야 하므로
    //벌크 삭제가 아니라 먼저 조회한다.
    //경계는 Cart.isExpired 와 같게 맞춘다(만료 시각 그 순간부터 만료).
    List<Cart> findAllByExpiresAtLessThanEqual(LocalDateTime now);
}
