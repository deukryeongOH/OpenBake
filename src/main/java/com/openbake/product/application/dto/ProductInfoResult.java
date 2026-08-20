package com.openbake.product.application.dto;

import com.openbake.product.domain.Category;
import com.openbake.product.domain.Type;

import java.time.LocalDate;
import java.util.Set;

public record ProductInfoResult(
        String name,
        String description,
        String imageUrl,
        int totalQuantity,
        int price,
        Set<LocalDate> pickUpAvailableDates,
        Category category,
        Long productId,
        int remainQuantity,
        Type type,
        Long sellerId
) {
    public static ProductInfoResult of(String name, String description, String imageUrl, int totalQuantity, int price, Set<LocalDate> pickupDates, Category category, Long productId, int remainQuantity, Type type, Long sellerId) {
        return new ProductInfoResult(name, description, imageUrl, totalQuantity, price, detach(pickupDates), category, productId, remainQuantity, type, sellerId);
    }

    /**
     * Product.pickUpAvailableDates는 지연 로딩 @ElementCollection이다.
     * 이 값을 그대로 담아 내보내면 트랜잭션이 끝난 뒤 응답을 직렬화하는 시점에
     * LazyInitializationException이 난다(open-in-view=false).
     * 여기서 한 번 복사해 영속성 컨텍스트와 분리한다 — 호출부마다 챙기면 빠뜨린다.
     */
    private static Set<LocalDate> detach(Set<LocalDate> pickupDates) {
        return pickupDates == null ? null : Set.copyOf(pickupDates);
    }
}
