package com.openbake.seller.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountVerificationConfirmRequest(
        @NotBlank String verificationCode
) {}
