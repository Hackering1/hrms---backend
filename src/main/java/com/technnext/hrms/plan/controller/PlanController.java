package com.technnext.hrms.plan.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.plan.dto.CurrentPlanResponse;
import com.technnext.hrms.plan.dto.PlanRequest;
import com.technnext.hrms.plan.dto.SubscribeRequest;
import com.technnext.hrms.plan.entity.CompanySubscription;
import com.technnext.hrms.plan.entity.SubscriptionPlan;
import com.technnext.hrms.plan.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Company plan & billing records — Super Admin / HR Admin only (same tier as payroll admin). */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
public class PlanController {

    private final PlanService service;

    @GetMapping
    public ApiResponse<List<SubscriptionPlan>> plans() {
        return ApiResponse.ok(service.listPlans());
    }

    @PostMapping
    public ApiResponse<SubscriptionPlan> create(@RequestBody PlanRequest body) {
        return ApiResponse.ok("Plan created", service.createPlan(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<SubscriptionPlan> update(@PathVariable Integer id, @RequestBody PlanRequest body) {
        return ApiResponse.ok("Plan updated", service.updatePlan(id, body));
    }

    @GetMapping("/current")
    public ApiResponse<CurrentPlanResponse> current() {
        return ApiResponse.ok(service.current());
    }

    @GetMapping("/history")
    public ApiResponse<List<CompanySubscription>> history() {
        return ApiResponse.ok(service.history());
    }

    @PostMapping("/subscribe")
    public ApiResponse<CurrentPlanResponse> subscribe(@RequestBody SubscribeRequest body) {
        return ApiResponse.ok("Subscription updated", service.subscribe(body));
    }

    @PostMapping("/cancel")
    public ApiResponse<CurrentPlanResponse> cancel() {
        return ApiResponse.ok("Subscription cancelled", service.cancel());
    }
}