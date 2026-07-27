package com.openbake.member.presentation.dto.auth;

import com.openbake.member.domain.Role;

public record LocalLoginResponse (
    Long memberId,
    String accessToken,
    String refreshToken,
    Role role
) {}
