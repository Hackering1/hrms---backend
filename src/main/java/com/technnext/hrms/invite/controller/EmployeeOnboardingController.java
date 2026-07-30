package com.technnext.hrms.invite.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.invite.dto.OnboardingCompleteRequest;
import com.technnext.hrms.invite.dto.OnboardingInfoResponse;
import com.technnext.hrms.invite.service.EmployeeInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Public — no authentication. The onboarding token itself is the credential
 * (see EmployeeInviteService.validateToken for the NOT_FOUND/EXPIRED/
 * ALREADY_USED/CANCELLED checks). Reachable at /api/public/onboarding/**,
 * which SecurityConfig already permits-all (no security config change needed).
 */
@RestController
@RequestMapping("/api/public/onboarding")
@RequiredArgsConstructor
public class EmployeeOnboardingController {

    private final EmployeeInviteService inviteService;

    @GetMapping("/{token}")
    public ApiResponse<OnboardingInfoResponse> getInfo(@PathVariable String token) {
        return ApiResponse.ok(inviteService.getOnboardingInfo(token));
    }

    @PostMapping(value = "/{token}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(
            @PathVariable String token,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("File uploaded", inviteService.uploadDocument(token, file));
    }

    @PostMapping("/{token}/complete")
    public ApiResponse<Void> complete(
            @PathVariable String token,
            @RequestBody OnboardingCompleteRequest body) {
        inviteService.completeOnboarding(token, body);
        return ApiResponse.ok("Registration completed", null);
    }
}