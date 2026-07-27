package com.openbake.member.presentation.dto.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest (
    @NotBlank @Size(min = 8, max = 20) String currentPassword,
    @NotBlank @Size(min = 8, max = 20) String newPassword
) {}
