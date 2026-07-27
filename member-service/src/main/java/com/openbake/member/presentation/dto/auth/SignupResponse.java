package com.openbake.member.presentation.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse (
        @Schema(description = "생성된 회원 ID", example = "1")
        Long memberId,
        @Schema(description = "가입한 이메일", example = "sejong@example.com")
        String email
) {}
