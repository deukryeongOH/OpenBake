package com.openbake.drop.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record DropProductInfoCommand(String name, String description, String image, LocalDateTime dropStart, LocalDateTime dropEnd,
                                     int totalQuantity, int LimitQuantity, int price, Set<LocalDate> pickupDates) {
    public static DropProductInfoCommand create(@NotBlank(message = "이름을 입력해주세요.") String name,
                                                @NotBlank(message = "상품 세부사항 및 설명을 입력해주세요.") String description,
                                                @NotBlank(message = "상품 관련 이미지를 첨부해주세요.") String image,
                                                @NotNull(message = "시작 시간을 입력해주세요.") LocalDateTime dropStart,
                                                @NotNull(message = "종료 시간을 입력해주세요.") LocalDateTime dropEnd,
                                                @Positive(message = "총 수량은 0보다 커야 합니다.") int total,
                                                @Positive(message = "1인당 제한 수량은 1개 이상이어야 합니다.") int limit,
                                                @Positive(message = "가격은 0보다 커야 합니다.") int price,
                                                @NotEmpty(message = "픽업 가능 날짜를 지정해주세요.") Set<LocalDate> pickupDates) {
        return new DropProductInfoCommand(name, description, image, dropStart, dropEnd, total, limit, price, pickupDates);
    }
}
