package com.openbake.product.domain;


import lombok.Getter;

@Getter
public enum Category {
    MEAL_BREADS("식사빵"), // 식사빵
    SWEET_BREADS("간식빵"), // 간식빵
    CAKES_TARTS("케이크/타르트"), // 케이크 & 타르트
    JAM_SPREAD("잼/스프레드"), // 스프레드
    COOKIES_BAKES("쿠키/구움과자"); // 쿠키 & 구움과자

    private final String message;

    Category(String message) {
        this.message = message;
    }

}
