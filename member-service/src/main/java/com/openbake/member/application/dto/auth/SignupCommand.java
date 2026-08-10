package com.openbake.member.application.dto.auth;

public record SignupCommand (
        String email,
        String password,
        String name,
        String phoneNumber
) {}
