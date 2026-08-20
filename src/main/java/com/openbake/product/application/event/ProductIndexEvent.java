package com.openbake.product.application.event;

import com.openbake.product.domain.Product;

public record ProductIndexEvent(
        EventType eventType,
        Long productId,
        Product product
) {
    public enum EventType {
        SAVED,   // 등록 또는 수정
        DELETED  // 삭제
    }

    public static ProductIndexEvent saved(Product product) {
        return new ProductIndexEvent(EventType.SAVED, product.getId(), product);
    }

    public static ProductIndexEvent deleted(Long productId) {
        return new ProductIndexEvent(EventType.DELETED, productId, null);
    }
}
