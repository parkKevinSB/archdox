package com.archdox.cloud.auth.api;

import com.archdox.cloud.auth.application.AuthService;
import com.archdox.cloud.auth.dto.AuthTokenResponse;
import com.archdox.cloud.auth.dto.LoginRequest;
import com.archdox.cloud.auth.dto.LogoutRequest;
import com.archdox.cloud.auth.dto.MeResponse;
import com.archdox.cloud.auth.dto.RefreshRequest;
import com.archdox.cloud.auth.dto.SignupRequest;
import com.archdox.cloud.global.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokenResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/auth/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/auth/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return authService.me((UserPrincipal) authentication.getPrincipal());
    }
}
