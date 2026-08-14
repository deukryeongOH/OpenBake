package com.openbake.product.presentation.dto;

import com.openbake.product.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.Set;

public record ProductInfoRequest(
        @NotBlank(message = "이름을 입력해주세요.")
        String name,

        @NotBlank(message = "상품 세부사항 및 설명을 입력해주세요.")
        String description,

        @NotBlank(message = "상품 관련 이미지를 첨부해주세요.")
        String imageUrl,

        @Positive(message = "총 수량은 0보다 커야 합니다.")
        int totalQuantity,

        @Positive(message = "가격은 0보다 커야 합니다.")
        int price,

        @NotEmpty(message = "픽업 가능 날짜를 지정해주세요.")
        Set<LocalDate> pickUpAvailableDates,

        @NotNull(message = "카테고리를 지정해주세요.")
        Category category
) {
}
