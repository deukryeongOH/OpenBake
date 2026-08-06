package com.openbake.member.application.dto.member;

public record PasswordChangeCommand(
        String currentPassword,
        String newPassword
) {}
