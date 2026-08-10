package com.openbake.member.presentation.dto.auth;

import com.openbake.member.application.dto.auth.LocalLoginResult;
import com.openbake.member.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record LocalLoginResponse (
    @Schema(description = "회원 ID", example = "1")
    Long memberId,
    @Schema(description = "Access Token")
    String accessToken,
    @Schema(description = "Refresh Token")
    String refreshToken,
    @Schema(description = "회원 권한", example = "CUSTOMER")
    Role role
) {
    public static LocalLoginResponse from(LocalLoginResult result) {
        return new LocalLoginResponse(result.memberId(), result.accessToken(), result.refreshToken(), result.role());
    }
}
