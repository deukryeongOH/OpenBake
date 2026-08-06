package com.openbake.member.presentation.dto.auth;

import com.openbake.member.application.dto.auth.LogoutCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @Schema(description = "무효화할 refresh token")
        @NotBlank String refreshToken
) {
        public LogoutCommand toCommand() {
                return new LogoutCommand(refreshToken);
        }
}
