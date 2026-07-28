package com.openbake.member.presentation.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @Schema(description = "무효화할 refresh token")
        @NotBlank String refreshToken
) {}
