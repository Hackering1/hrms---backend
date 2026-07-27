package com.technnext.hrms.document.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.document.dto.EmployeeDocumentCreate;
import com.technnext.hrms.document.entity.EmployeeDocument;
import com.technnext.hrms.document.service.EmployeeDocumentService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/employee-documents")
@RequiredArgsConstructor
public class EmployeeDocumentController {

    private final EmployeeDocumentService service;
    private final CurrentUserService currentUser;

    /** Read an employee's documents — self, a manager's team member, or (super admin) anyone. */
    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<EmployeeDocument>> getByEmployee(
            @PathVariable UUID employeeId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.getByEmployee(employeeId));
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<List<EmployeeDocument>> getExpiring(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before) {
        return ApiResponse.ok(service.getExpiringBefore(before));
    }

    /**
     * Upload a document for an employee. Scoped: an employee may add to their own
     * file, a manager to their team, a super admin to anyone. Previously any
     * authenticated user could attach a document to ANY employee's record.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<EmployeeDocument> create(
            @RequestBody EmployeeDocumentCreate body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, body.employeeId());
        return ApiResponse.ok("Document added", service.create(body));
    }

    /**
     * Delete a document. Managers may now delete documents for their own team
     * (Mgr #5) — previously this was HR/Super-Admin only, so the UI offered a
     * delete button to managers that the API rejected. Team membership is
     * re-checked here so a manager can only delete within their scope.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<Void> delete(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        EmployeeDocument doc = service.getById(id);
        currentUser.assertCanAccessEmployee(principal, doc.getEmployeeId());
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}