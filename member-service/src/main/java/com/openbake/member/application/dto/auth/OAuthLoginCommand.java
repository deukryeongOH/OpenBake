package com.openbake.member.application.dto.auth;

import com.openbake.member.domain.AuthProvider;

public record OAuthLoginCommand(
        AuthProvider provider,
        String idToken
) {}
