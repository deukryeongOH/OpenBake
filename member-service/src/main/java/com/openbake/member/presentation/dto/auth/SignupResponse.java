package com.openbake.member.presentation.dto.auth;

public record SignupResponse (
        Long memberId,
        String email
) {}
