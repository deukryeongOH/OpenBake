package com.openbake.member.presentation.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(
        @NotBlank String idToken
) {}
