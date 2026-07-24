package com.technnext.hrms.leave.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.leave.entity.LeaveType;
import com.technnext.hrms.leave.service.LeaveTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/leave-types")
@RequiredArgsConstructor
public class LeaveTypeController {

    private final LeaveTypeService service;

    @GetMapping
    public ApiResponse<List<LeaveType>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<LeaveType> getById(@PathVariable Integer id) {
        return ApiResponse.ok(service.getById(id));
    }

    // Master data: Super Admin + Manager (your confirmed rule).
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<LeaveType> create(@Valid @RequestBody LeaveType body) {
        return ApiResponse.ok("Created", service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<LeaveType> update(@PathVariable Integer id, @Valid @RequestBody LeaveType body) {
        return ApiResponse.ok("Updated", service.update(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER')")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}