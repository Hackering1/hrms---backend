package com.technnext.hrms.organization.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.organization.entity.Designation;
import com.technnext.hrms.organization.service.DesignationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/designations")
@RequiredArgsConstructor
public class DesignationController {

    private final DesignationService service;

    @GetMapping
    public ApiResponse<List<Designation>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Designation> getById(@PathVariable Integer id) {
        return ApiResponse.ok(service.getById(id));
    }

    // Master data: Super Admin + Manager (your confirmed rule).
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<Designation> create(@Valid @RequestBody Designation body) {
        return ApiResponse.ok("Created", service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<Designation> update(@PathVariable Integer id, @Valid @RequestBody Designation body) {
        return ApiResponse.ok("Updated", service.update(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}