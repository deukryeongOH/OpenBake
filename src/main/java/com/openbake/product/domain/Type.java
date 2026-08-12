package com.openbake.product.domain;

import lombok.Getter;

@Getter
public enum Type {
    GENERAL("일반 상품"),
    DROP("드롭 상품");

    private final String message;

    Type(String message) {
        this.message = message;
    }
}
