package com.openbake.member.presentation.dto.member;

import com.openbake.member.application.dto.member.PasswordChangeCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest (
    @Schema(description = "본인 확인용 현재 비밀번호", example = "oldPassword123")
    @NotBlank @Size(min = 8, max = 20) String currentPassword,
    @Schema(description = "변경할 새 비밀번호", example = "newPassword456")
    @NotBlank @Size(min = 8, max = 20) String newPassword
) {
    public PasswordChangeCommand toCommand() {
        return new PasswordChangeCommand(currentPassword, newPassword);
    }
}
