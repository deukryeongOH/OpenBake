package com.openbake.member.application.dto.auth;

public record LocalLoginCommand(
        String email,
        String password
) {}
