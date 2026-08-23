package com.openbake.order.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 주문서(진행 중) 화면에 그리는 항목.
 *
 * <b>판매자를 담지 않는다.</b> 주문 진행 중에는 판매자를 노출하지 않고,
 * 주문 내역(완료·취소 후 조회)에서만 상호명까지 보여준다.
 */
public record OrderSheetItem(
        Long orderItemId,
        String productName,
        String imageUrl,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        LocalDate pickUpDate
) {
}
