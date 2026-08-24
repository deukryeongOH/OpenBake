package com.openbake.order.application.port;

import com.openbake.order.application.port.dto.CartItemInfo;

import java.util.List;

public interface CartPort {

    /**
     * 고른 항목만 가져온다. 회원 장바구니 안에서만 찾고, 하나라도 없으면 통째로 거부한다.
     *
     * 수량·픽업일은 요청 본문이 아니라 <b>여기서 읽는다.</b> 클라이언트가 보내는 것은
     * "무엇을 살지"(의도)까지고, "몇 개·언제"(사실)는 서버가 장바구니에서 확인한다.
     */
    List<CartItemInfo> findItemsForOrder(Long memberId, List<Long> cartItemIds);

    /**
     * 주문한 항목만 장바구니에서 뺀다. <b>결제가 성공한 뒤</b>의 뒷정리다.
     *
     * 멱등하다 — 이미 없는 id 는 무시한다. 여기서 예외를 던지면 성공한 결제까지 롤백된다.
     */
    void removeItems(Long memberId, List<Long> cartItemIds);
}
