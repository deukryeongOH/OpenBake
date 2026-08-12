package com.openbake.cart.application.port;

import com.openbake.cart.application.port.dto.SellerInfo;

import java.util.Optional;

/**
 * 판매자 조회 포트.
 * 장바구니 상세의 상호명 표시에만 쓰이므로 없으면 빈 값으로 돌려준다(방어).
 */
public interface SellerPort {

    Optional<SellerInfo> findSeller(Long sellerId);
}
