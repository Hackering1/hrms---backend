package com.technnext.hrms.performance.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.performance.dto.CycleRequest;
import com.technnext.hrms.performance.dto.CycleStatusRequest;
import com.technnext.hrms.performance.dto.GoalRequest;
import com.technnext.hrms.performance.dto.ManagerReviewRequest;
import com.technnext.hrms.performance.dto.ReviewResponse;
import com.technnext.hrms.performance.dto.SelfReviewRequest;
import com.technnext.hrms.performance.entity.PerformanceCycle;
import com.technnext.hrms.performance.entity.PerformanceGoal;
import com.technnext.hrms.performance.service.PerformanceService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService service;
    private final CurrentUserService currentUser;

    // ── Cycles (HR tier manages; everyone can list) ─────────────────────────────

    @GetMapping("/cycles")
    public ApiResponse<List<PerformanceCycle>> cycles() {
        return ApiResponse.ok(service.listCycles());
    }

    @PostMapping("/cycles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<PerformanceCycle> createCycle(@RequestBody CycleRequest body) {
        return ApiResponse.ok("Cycle created", service.createCycle(body));
    }

    @PutMapping("/cycles/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<PerformanceCycle> updateCycle(@PathVariable Integer id, @RequestBody CycleRequest body) {
        return ApiResponse.ok("Cycle updated", service.updateCycle(id, body));
    }

    @PutMapping("/cycles/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<PerformanceCycle> setStatus(@PathVariable Integer id, @RequestBody CycleStatusRequest body) {
        return ApiResponse.ok("Cycle updated", service.setCycleStatus(id, body.status()));
    }

    // ── Goals ───────────────────────────────────────────────────────────────────

    @GetMapping("/goals/me")
    public ApiResponse<List<PerformanceGoal>> myGoals(
            @RequestParam Integer cycleId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(service.goals(currentUser.ownEmployeeId(principal), cycleId));
    }

    @GetMapping("/goals/employee/{employeeId}")
    public ApiResponse<List<PerformanceGoal>> employeeGoals(
            @PathVariable UUID employeeId,
            @RequestParam Integer cycleId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.goals(employeeId, cycleId));
    }

    /** Self, or (manager / super admin) a team member via employeeId. */
    @PostMapping("/goals")
    public ApiResponse<PerformanceGoal> createGoal(
            @RequestBody GoalRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UUID target = currentUser.resolveActingEmployeeId(principal, body.employeeId());
        return ApiResponse.ok("Goal created", service.createGoal(target, body));
    }

    @PutMapping("/goals/{id}")
    public ApiResponse<PerformanceGoal> updateGoal(
            @PathVariable Integer id,
            @RequestBody GoalRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, service.getGoal(id).getEmployeeId());
        return ApiResponse.ok("Goal updated", service.updateGoal(id, body));
    }

    @DeleteMapping("/goals/{id}")
    public ApiResponse<Void> deleteGoal(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, service.getGoal(id).getEmployeeId());
        service.deleteGoal(id);
        return ApiResponse.ok("Goal deleted", null);
    }

    // ── Reviews ─────────────────────────────────────────────────────────────────

    @GetMapping("/reviews/me")
    public ApiResponse<ReviewResponse> myReview(
            @RequestParam Integer cycleId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(service.myReview(currentUser.ownEmployeeId(principal), cycleId));
    }

    @PostMapping("/reviews/me")
    public ApiResponse<ReviewResponse> submitSelf(
            @RequestBody SelfReviewRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok("Self review submitted",
                service.submitSelfReview(currentUser.ownEmployeeId(principal), body));
    }

    /** Reviews of people the caller manages (never their own row — that stays under /reviews/me). */
    @GetMapping("/reviews/team")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<List<ReviewResponse>> team(
            @RequestParam Integer cycleId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        List<ReviewResponse> all = service.cycleReviews(cycleId);
        Optional<UUID> own = currentUser.ownEmployeeIdOrEmpty(principal);
        boolean admin = currentUser.isSuperAdmin(principal);
        Set<UUID> scope = admin ? null : currentUser.accessibleEmployeeIds(principal);
        return ApiResponse.ok(all.stream()
                .filter(r -> own.isEmpty() || !own.get().equals(r.employeeId()))
                .filter(r -> admin || scope.contains(r.employeeId()))
                .toList());
    }

    @PutMapping("/reviews/{id}/manager")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<ReviewResponse> complete(
            @PathVariable Integer id,
            @RequestBody ManagerReviewRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        ReviewResponse existing = service.getReview(id);
        currentUser.assertCanAccessEmployee(principal, existing.employeeId());
        UUID reviewer = currentUser.ownEmployeeIdOrEmpty(principal).orElse(null);
        return ApiResponse.ok("Review completed", service.completeReview(id, reviewer, body));
    }
}