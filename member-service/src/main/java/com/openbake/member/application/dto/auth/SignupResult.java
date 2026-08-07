package com.openbake.member.application.dto.auth;

public record SignupResult(
        Long memberId,
        String email
) {}
