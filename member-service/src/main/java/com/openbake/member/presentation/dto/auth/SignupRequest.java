package com.openbake.member.presentation.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest (
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 20) String password,
        @NotBlank String name,
        @NotBlank String phoneNumber
) {}
