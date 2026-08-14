package com.openbake.drop.application.dto;

import com.openbake.drop.domain.DropStatus;
import com.openbake.product.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record DropInfoCommand(String name, String description, String image, LocalDateTime dropStart, LocalDateTime dropEnd, DropStatus dropStatus,
                              int totalQuantity, int limitQuantity, int price, Set<LocalDate> pickupDates, Category category) {
    public static DropInfoCommand create(@NotBlank(message = "드롭 상품명을 입력해주세요.") String name,
                                         @NotBlank(message = "드롭 상품 세부사항 및 설명을 입력해주세요.") String description,
                                         @NotBlank(message = "드롭 상품 관련 이미지를 첨부해주세요.") String image,
                                         @NotNull(message = "드롭 시작 시간을 입력해주세요.") LocalDateTime dropStart,
                                         @NotNull(message = "드롭 종료 시간을 입력해주세요.") LocalDateTime dropEnd,
                                         @Positive(message = "총 수량은 0보다 커야 합니다.") int total,
                                         @Positive(message = "1인당 제한 수량은 1개 이상이어야 합니다.") int limit,
                                         @Positive(message = "가격은 0보다 커야 합니다.") int price,
                                         @NotEmpty(message = "픽업 가능 날짜를 지정해주세요.") Set<LocalDate> pickupDates,
                                         @NotNull(message = "카테고리를 선택해주세요.") Category category) {
        return new DropInfoCommand(name, description, image, dropStart, dropEnd, DropStatus.UPCOMING, total, limit, price, pickupDates, category);
    }
}
