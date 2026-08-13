package com.openbake.order.application.port;

import com.openbake.order.application.port.dto.CartInfo;

import java.util.Optional;

public interface CartPort {
    Optional<CartInfo> findCart(Long memberId);

    void deleteCart(Long memberId);
}
