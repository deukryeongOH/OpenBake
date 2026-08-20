package com.openbake.cart.infrastructure;

import com.openbake.cart.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartJpaRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);
}
