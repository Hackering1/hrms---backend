package com.technnext.hrms.auth.controller;

import com.technnext.hrms.auth.dto.*;
import com.technnext.hrms.auth.service.AuthService;
import com.technnext.hrms.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Login successful", authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok("Token refreshed", authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ApiResponse.ok("Logged out", null);
    }

    /** Any signed-in user can change their own password. */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            org.springframework.security.core.Authentication auth) {
        authService.changePassword(auth.getName(), request);
        return ApiResponse.ok("Password changed", null);
    }

    /** Super admin: reset another user's password (by their login email). */
    @PostMapping("/admin/reset-password")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN')")
    public ApiResponse<Void> adminResetPassword(@RequestBody java.util.Map<String, String> body) {
        authService.adminResetPasswordByEmail(body.get("email"), body.get("newPassword"));
        return ApiResponse.ok("Password reset", null);
    }

    /**
     * Creating accounts is an admin action, so this endpoint sits under /api/auth
     * but is protected. Finance Admin and Super Admin can register users; HR
     * roles may register non-admin accounts but cannot create a Super Admin.
     */
    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','FINANCE_ADMIN') and "
            + "(#request.roleName() != 'SUPER_ADMIN' or hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN'))")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("User registered", authService.register(request));
    }
}
