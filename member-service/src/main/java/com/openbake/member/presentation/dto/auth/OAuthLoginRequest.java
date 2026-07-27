package com.openbake.member.presentation.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(
        @Schema(description = "OAuth 공급자가 발급한 ID 토큰 (인가 코드 아님)", example = "eyJhbGciOi...")
        @NotBlank String idToken
) {}
