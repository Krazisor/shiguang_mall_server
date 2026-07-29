package org.dhu.shiguang_market.identity.controller;

import jakarta.validation.Valid;
import org.dhu.shiguang_market.common.api.ApiResponse;
import org.dhu.shiguang_market.common.api.CommonViews.UserSummary;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.CurrentUserView;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.LoginRequest;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.LoginView;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.RegisterRequest;
import org.dhu.shiguang_market.identity.dto.IdentityDtos.UpdateProfileRequest;
import org.dhu.shiguang_market.identity.service.IdentityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final IdentityService identityService;

    public AuthController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<UserSummary>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(identityService.register(request)));
    }

    @PostMapping("/auth/login")
    public ApiResponse<LoginView> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(identityService.login(request));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout() {
        identityService.logout();
        return ApiResponse.success(null);
    }

    @GetMapping("/auth/me")
    public ApiResponse<CurrentUserView> me() {
        return ApiResponse.success(identityService.me());
    }

    @PatchMapping("/users/me")
    public ApiResponse<CurrentUserView> update(@RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(identityService.update(request));
    }
}
