package com.openbake.seller.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ApplicationCreateRequest(
        @NotBlank String bakeryName,
        @NotBlank String businessNumber,
        @NotBlank String businessAddress,
        @NotBlank String businessRepresentativeName
) {}
