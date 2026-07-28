package com.openbake.member.presentation.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record OAuthLoginResponse(
        @Schema(description = "회원 ID", example = "1")
        Long memberId,
        @Schema(description = "Access Token")
        String accessToken,
        @Schema(description = "Refresh Token")
        String refreshToken,
        @Schema(description = "OAuth 공급자로부터 받은 이메일", example = "user@gmail.com")
        String email,
        @Schema(description = "OAuth 공급자로부터 받은 이름", example = "홍길동")
        String name,
        @Schema(description = "신규 가입 여부", example = "false")
        boolean newMember
) {}
