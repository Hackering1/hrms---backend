package com.technnext.hrms.employee.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.employee.dto.EmployeeContactDto;
import com.technnext.hrms.employee.dto.EmployeeContactUpsertRequest;
import com.technnext.hrms.employee.service.EmployeeContactService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Address / emergency contact / phone details for a single employee — backed
 * by employee_contacts, kept separate from the core employee record on
 * purpose (see EmployeeContactDto). Same access rule as GET/PUT
 * /api/employees/{id}: self, a manager for their own team, or an admin.
 */
@RestController
@RequestMapping("/api/employees/{id}/contact")
@RequiredArgsConstructor
public class EmployeeContactController {

    private final EmployeeContactService service;
    private final CurrentUserService currentUser;

    @GetMapping
    public ApiResponse<EmployeeContactDto> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, id);
        return ApiResponse.ok(service.getByEmployeeId(id));
    }

    @PutMapping
    public ApiResponse<EmployeeContactDto> update(
            @PathVariable UUID id,
            @RequestBody EmployeeContactUpsertRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, id);
        return ApiResponse.ok("Contact details updated", service.upsert(id, body));
    }
}