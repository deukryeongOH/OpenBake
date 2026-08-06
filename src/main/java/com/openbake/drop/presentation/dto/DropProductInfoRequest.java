package com.openbake.drop.presentation.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record DropProductInfoRequest(
        @NotBlank(message = "이름을 입력해주세요.")
        String name,

        @NotBlank(message = "상품 세부사항 및 설명을 입력해주세요.")
        String description,

        @NotBlank(message = "상품 관련 이미지를 첨부해주세요.")
        String imageUrl,

        @NotEmpty(message = "픽업 가능 날짜를 지정해주세요.")
        Set<LocalDate> pickUpAvailableDates,

        @NotNull(message = "시작 시간을 입력해주세요.")
        LocalDateTime dropStart,

        @NotNull(message = "종료 시간을 입력해주세요.")
        LocalDateTime dropEnd,

        @Positive(message = "1인당 제한 수량은 1개 이상이어야 합니다.")
        int limitQuantity,

        @Positive(message = "가격은 0보다 커야 합니다.")
        int price,

        @Positive(message = "총 수량은 0보다 커야 합니다.")
        int totalQuantity
) {
}
