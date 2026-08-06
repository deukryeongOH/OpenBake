package com.openbake.member.application.dto.auth;

import com.openbake.member.domain.Role;

public record LocalLoginResult(
        Long memberId,
        String accessToken,
        String refreshToken,
        Role role
) {}
