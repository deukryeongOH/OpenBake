package com.openbake.member.presentation.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReissueResponse(
        @Schema(description = "재발급된 Access Token")
        String accessToken,
        @Schema(description = "재발급된 Refresh Token")
        String refreshToken
) {}
