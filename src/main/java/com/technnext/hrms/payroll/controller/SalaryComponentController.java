package com.technnext.hrms.payroll.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.payroll.entity.SalaryComponent;
import com.technnext.hrms.payroll.service.SalaryComponentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Payroll is financial/statutory data — deliberately SUPER_ADMIN + HR_ADMIN only,
// narrower than the MANAGER_PLUS access Leave/Organization master data gets.
@RestController
@RequestMapping("/api/payroll/salary-components")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
public class SalaryComponentController {

    private final SalaryComponentService service;

    @GetMapping
    public ApiResponse<List<SalaryComponent>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SalaryComponent> getById(@PathVariable Integer id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    public ApiResponse<SalaryComponent> create(@Valid @RequestBody SalaryComponent body) {
        return ApiResponse.ok("Created", service.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<SalaryComponent> update(@PathVariable Integer id, @Valid @RequestBody SalaryComponent body) {
        return ApiResponse.ok("Updated", service.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deactivated", null);
    }
}