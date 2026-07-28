package com.openbake.member.presentation.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ReissueRequest(
        @Schema(description = "발급받은 refresh token")
        @NotBlank String refreshToken
) {}
