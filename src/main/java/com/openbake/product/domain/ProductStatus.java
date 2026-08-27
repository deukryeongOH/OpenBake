package com.openbake.product.domain;

import lombok.Getter;

@Getter
public enum ProductStatus {
    SELLING("판매중"),
    SOLD_OUT("품절"),
    DELETED("삭제됨");

    private final String message;

    ProductStatus(String message) {
        this.message = message;
    }
}
