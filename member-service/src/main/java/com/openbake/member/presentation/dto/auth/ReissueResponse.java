package com.openbake.member.presentation.dto.auth;

public record ReissueResponse(
        String accessToken,
        String refreshToken
) {}
