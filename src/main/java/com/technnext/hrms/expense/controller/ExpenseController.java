package com.technnext.hrms.expense.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.expense.dto.ExpenseCategoryRequest;
import com.technnext.hrms.expense.dto.ExpenseClaimCreate;
import com.technnext.hrms.expense.dto.ExpenseClaimResponse;
import com.technnext.hrms.expense.dto.ExpenseDecision;
import com.technnext.hrms.expense.dto.ExpensePayment;
import com.technnext.hrms.expense.entity.ExpenseCategory;
import com.technnext.hrms.expense.service.ExpenseService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService service;
    private final CurrentUserService currentUser;

    // ── Categories ──────────────────────────────────────────────────────────────

    @GetMapping("/categories")
    public ApiResponse<List<ExpenseCategory>> activeCategories() {
        return ApiResponse.ok(service.listCategories(true));
    }

    @GetMapping("/categories/all")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<List<ExpenseCategory>> allCategories() {
        return ApiResponse.ok(service.listCategories(false));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<ExpenseCategory> createCategory(@RequestBody ExpenseCategoryRequest body) {
        return ApiResponse.ok("Category created", service.createCategory(body));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<ExpenseCategory> updateCategory(@PathVariable Integer id, @RequestBody ExpenseCategoryRequest body) {
        return ApiResponse.ok("Category updated", service.updateCategory(id, body));
    }

    // ── My claims (always the caller's own) ─────────────────────────────────────

    @GetMapping("/claims/me")
    public ApiResponse<List<ExpenseClaimResponse>> myClaims(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(service.myClaims(currentUser.ownEmployeeId(principal)));
    }

    @PostMapping("/claims")
    public ApiResponse<ExpenseClaimResponse> submit(
            @RequestBody ExpenseClaimCreate body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok("Claim submitted",
                service.submit(currentUser.ownEmployeeId(principal), principal.getUser().getId(), body));
    }

    @PutMapping("/claims/{id}/cancel")
    public ApiResponse<ExpenseClaimResponse> cancel(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok("Claim cancelled", service.cancel(id, currentUser.ownEmployeeId(principal)));
    }

    // ── Review (manager / HR, scoped to their reports) ──────────────────────────

    @GetMapping("/claims/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<List<ExpenseClaimResponse>> pending(@AuthenticationPrincipal CustomUserDetails principal) {
        List<ExpenseClaimResponse> all = service.byStatus("SUBMITTED");
        if (currentUser.isSuperAdmin(principal)) return ApiResponse.ok(all);
        Set<UUID> scope = currentUser.accessibleEmployeeIds(principal);
        return ApiResponse.ok(all.stream().filter(c -> scope.contains(c.employeeId())).toList());
    }

    @GetMapping("/claims/{id}")
    public ApiResponse<ExpenseClaimResponse> getById(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        ExpenseClaimResponse c = service.getById(id);
        currentUser.assertCanAccessEmployee(principal, c.employeeId());
        return ApiResponse.ok(c);
    }

    @PutMapping("/claims/{id}/decision")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<ExpenseClaimResponse> decide(
            @PathVariable Integer id,
            @RequestBody ExpenseDecision body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        ExpenseClaimResponse existing = service.getById(id);
        currentUser.assertCanAccessEmployee(principal, existing.employeeId());
        UUID reviewer = currentUser.ownEmployeeIdOrEmpty(principal).orElse(null);
        return ApiResponse.ok("Decision recorded", service.decide(id, reviewer, body));
    }

    // ── Payment (HR tier only — NOT managers) ───────────────────────────────────

    @GetMapping("/claims/payable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<List<ExpenseClaimResponse>> payable() {
        return ApiResponse.ok(service.byStatus("APPROVED"));
    }

    @PutMapping("/claims/{id}/pay")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<ExpenseClaimResponse> pay(@PathVariable Integer id, @RequestBody(required = false) ExpensePayment body) {
        return ApiResponse.ok("Marked as paid", service.markPaid(id, body));
    }
}