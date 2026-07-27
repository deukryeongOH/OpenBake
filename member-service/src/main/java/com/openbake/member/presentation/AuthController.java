package com.openbake.member.presentation;

import com.openbake.common.response.ApiResponse;
import com.openbake.member.application.AuthService;
import com.openbake.member.domain.AuthProvider;
import com.openbake.member.presentation.dto.auth.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @PostMapping("/oauth/{provider}")
    public ApiResponse<OAuthLoginResponse> loginWithOAuth(
            @PathVariable String provider, @Valid @RequestBody OAuthLoginRequest request) {
        AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase());
        return ApiResponse.ok(authService.loginOrSignupWithOAuth(authProvider, request));
    }

    @PostMapping("/login")
    public ApiResponse<LocalLoginResponse> login(@Valid @RequestBody LocalLoginRequest request) {
        return ApiResponse.ok(authService.localLogin(request));
    }

    @PostMapping("/reissue")
    public ApiResponse<ReissueResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ApiResponse.ok(authService.reissue(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.ok();
    }
}
