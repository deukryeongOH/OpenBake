package com.openbake.cart.application;

import com.openbake.cart.domain.Cart;
import java.time.LocalDate;

public record CartPickupDateResult(
        Long cartId,
        LocalDate pickupDate
) {
    public static CartPickupDateResult from(Cart cart) {
        return new CartPickupDateResult(
                cart.getCartId(),
                cart.getPickupDate()
        );
    }
}
