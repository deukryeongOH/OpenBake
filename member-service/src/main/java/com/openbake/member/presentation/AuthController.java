package com.openbake.member.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.member.application.AuthService;
import com.openbake.member.domain.AuthProvider;
import com.openbake.member.presentation.dto.auth.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입 / 로그인 / 토큰 재발급 / 로그아웃")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "로컬 회원가입",
            description = "이메일/비밀번호로 신규 회원가입을 처리합니다. Member와 AuthCredential(provider=LOCAL)을 함께 생성합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. (필수값 누락/형식 오류)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "ME001 이미 존재하는 리소스입니다. (이메일 중복)")
    })
    @SecurityRequirements
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(SignupResponse.from(authService.signup(request.toCommand())));
    }

    @Operation(
            summary = "OAuth 로그인/가입",
            description = "클라이언트가 발급받은 ID 토큰을 검증합니다. providerId로 기존 회원이면 로그인, 없으면 Member+AuthCredential을 새로 생성합니다. 현재 provider는 google만 지원합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "C001 잘못된 요청입니다. (idToken 누락, 지원하지 않는 provider)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "ME001 이미 존재하는 리소스입니다. (동일 이메일 LOCAL 계정 존재)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "C003 대상을 찾을 수 없습니다. (정합성 오류, 사실상 발생하지 않음)")
    })
    @SecurityRequirements
    @PostMapping("/oauth/{provider}")
    public ApiResponse<OAuthLoginResponse> loginWithOAuth(
            @Parameter(description = "OAuth 공급자", example = "google") @PathVariable String provider,
            @Valid @RequestBody OAuthLoginRequest request) {
        AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase());
        return ApiResponse.ok(OAuthLoginResponse.from(authService.loginOrSignupWithOAuth(request.toCommand(authProvider))));
    }

    @Operation(
            summary = "로컬 로그인",
            description = "이메일/비밀번호로 로그인하고 access/refresh 토큰을 발급합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME003 이메일 또는 비밀번호가 일치하지 않습니다.")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ApiResponse<LocalLoginResponse> login(@Valid @RequestBody LocalLoginRequest request) {
        return ApiResponse.ok(LocalLoginResponse.from(authService.localLogin(request.toCommand())));
    }

    @Operation(
            summary = "Access Token 재발급",
            description = "만료된 access token을 refresh token으로 재발급합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다.")
    })
    @SecurityRequirements
    @PostMapping("/reissue")
    public ApiResponse<ReissueResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ApiResponse.ok(ReissueResponse.from(authService.reissue(request.toCommand())));
    }

    @Operation(
            summary = "로그아웃",
            description = "서버에 저장된 refresh token을 무효화하고, 발급된 access token을 블랙리스트 처리합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "ME002 유효하지 않은 인증 토큰입니다.")
    })
    @SecurityRequirements
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.toCommand());
        return ApiResponse.ok();
    }
}
