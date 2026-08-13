package com.openbake.order.application.port.dto;

public record SellerInfo (
        Long sellerId,
        //판매자 연락처는 seller 가 직접 갖지 않아, 연결된 회원을 찾으려면 이 값이 필요하다.
        Long memberId,
        String sellerName,
        String address
) {
}
