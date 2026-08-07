package com.openbake.member.application.dto.auth;

public record LogoutCommand(
        String refreshToken
) {}
