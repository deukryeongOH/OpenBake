package com.openbake.product.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ImageUploadUrlRequest(@NotBlank(message = "Content-Type을 입력해주세요.") String contentType) {
}
