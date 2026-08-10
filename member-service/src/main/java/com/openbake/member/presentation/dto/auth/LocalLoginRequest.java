package com.openbake.member.presentation.dto.auth;

import com.openbake.member.application.dto.auth.LocalLoginCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocalLoginRequest (
        @Schema(description = "가입한 이메일", example = "sejong@example.com")
        @Email @NotBlank String email,
        @Schema(description = "비밀번호", example = "password123!")
        @NotBlank @Size(min = 8, max = 20) String password
) {
        public LocalLoginCommand toCommand() {
                return new LocalLoginCommand(email, password);
        }
}
