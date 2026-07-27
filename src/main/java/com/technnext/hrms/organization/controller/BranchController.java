package com.technnext.hrms.organization.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.organization.entity.Branch;
import com.technnext.hrms.organization.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService service;

    @GetMapping
    public ApiResponse<List<Branch>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Branch> getById(@PathVariable Integer id) {
        return ApiResponse.ok(service.getById(id));
    }

    // Master data: Super Admin + Manager (your confirmed rule).
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<Branch> create(@Valid @RequestBody Branch body) {
        return ApiResponse.ok("Created", service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<Branch> update(@PathVariable Integer id, @Valid @RequestBody Branch body) {
        return ApiResponse.ok("Updated", service.update(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}