package com.openbake.drop.application.dto;

import com.openbake.drop.domain.DropProduct;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public record ConfirmEntryResponse(
    String name, String description, String imageUrl, int price, int limitQuantity, int remainQuantity, Set<LocalDate> pickupDates
) {

    // pickupDates가 LAZY 컬렉션이라, 호출부의 트랜잭션(세션)이 열려 있는 동안
    // 새 HashSet으로 복사해둬야 한다. 참조만 넘기면 세션이 끝난 뒤(JSON 직렬화 시점)
    // 초기화를 시도하다 LazyInitializationException이 난다.
    public static ConfirmEntryResponse of(DropProduct dropProduct, int limitQuantity, int remainQuantity, Set<LocalDate> pickupDates){
        return new ConfirmEntryResponse(
                dropProduct.getName(), dropProduct.getDescription(),
                dropProduct.getImageUrl(), dropProduct.getPrice(), limitQuantity, remainQuantity, new HashSet<>(pickupDates)
        );
    }
}
