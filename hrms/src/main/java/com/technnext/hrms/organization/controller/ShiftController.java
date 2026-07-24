package com.technnext.hrms.organization.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.organization.entity.Shift;
import com.technnext.hrms.organization.service.ShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService service;

    @GetMapping
    public ApiResponse<List<Shift>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Shift> getById(@PathVariable Integer id) {
        return ApiResponse.ok(service.getById(id));
    }

    // Master data: Super Admin + Manager (your confirmed rule).
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<Shift> create(@Valid @RequestBody Shift body) {
        return ApiResponse.ok("Created", service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<Shift> update(@PathVariable Integer id, @Valid @RequestBody Shift body) {
        return ApiResponse.ok("Updated", service.update(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}