package com.openbake.member.application.dto.auth;

public record OAuthLoginResult(
        Long memberId,
        String accessToken,
        String refreshToken,
        String email,
        String name,
        boolean newMember
) {}
