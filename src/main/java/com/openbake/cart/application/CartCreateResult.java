package com.openbake.cart.application;

import com.openbake.cart.domain.Cart;
import com.openbake.cart.domain.CartItem;

import java.time.LocalDateTime;

public record CartCreateResult (
        Long cartId,
        Long dropId,
        Integer quantity,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
){

    public static CartCreateResult from(Cart cart) {
        CartItem item = cart.getItems();
        return new CartCreateResult(
                cart.getCartId(),
                item.getDropId(),
                item.getQuantity(),
                cart.getExpiresAt(),
                cart.getCreatedAt()
        );
    }
}