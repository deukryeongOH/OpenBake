package com.openbake.member.presentation.dto.auth;

import com.openbake.member.application.dto.auth.SignupCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest (
        @Schema(description = "로그인 이메일 (UNIQUE)", example = "sejong@example.com")
        @Email @NotBlank String email,
        @Schema(description = "비밀번호 (8자 이상 20자 이하)", example = "password123!")
        @NotBlank @Size(min = 8, max = 20) String password,
        @Schema(description = "이름", example = "이세종")
        @NotBlank String name,
        @Schema(description = "휴대폰 번호", example = "010-1234-5678")
        @NotBlank String phoneNumber
) {
        public SignupCommand toCommand() {
                return new SignupCommand(email, password, name, phoneNumber);
        }
}
