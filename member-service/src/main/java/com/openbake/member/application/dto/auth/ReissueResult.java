package com.openbake.member.application.dto.auth;

public record ReissueResult(
        String accessToken,
        String refreshToken
) {}
