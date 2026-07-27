package com.technnext.hrms.letter.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.letter.dto.GenerateLetterRequest;
import com.technnext.hrms.letter.dto.LetterPreview;
import com.technnext.hrms.letter.entity.GeneratedLetter;
import com.technnext.hrms.letter.service.GeneratedLetterService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/generated-letters")
@RequiredArgsConstructor
public class GeneratedLetterController {

    private final GeneratedLetterService service;
    private final CurrentUserService currentUser;

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<List<GeneratedLetter>> getByEmployee(
            @PathVariable UUID employeeId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.getByEmployee(employeeId));
    }

    @GetMapping("/preview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<LetterPreview> preview(
            @RequestParam Integer templateId, @RequestParam UUID employeeId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.preview(templateId, employeeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<GeneratedLetter> generate(@RequestBody GenerateLetterRequest body) {
        return ApiResponse.ok("Letter generated", service.generate(body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}