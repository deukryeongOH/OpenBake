package com.openbake.member.presentation.dto.auth;

import com.openbake.member.application.dto.auth.ReissueResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReissueResponse(
        @Schema(description = "재발급된 Access Token")
        String accessToken,
        @Schema(description = "재발급된 Refresh Token")
        String refreshToken
) {
        public static ReissueResponse from(ReissueResult result) {
                return new ReissueResponse(result.accessToken(), result.refreshToken());
        }
}
