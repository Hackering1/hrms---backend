package com.technnext.hrms.employee.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.employee.dto.EmployeeRequest;
import com.technnext.hrms.employee.dto.EmployeeResponse;
import com.technnext.hrms.employee.service.EmployeeService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService service;
    private final CurrentUserService currentUser;

    /**
     * List employees, SCOPED to the caller:
     *   SUPER_ADMIN / HR -> everyone
     *   MANAGER          -> their own team + themselves
     *   EMPLOYEE         -> themselves only
     *
     * Previously this returned the entire employee table (including Aadhaar / PAN /
     * bank details) to any authenticated user. It is now filtered via
     * CurrentUserService.accessibleEmployeeIds().
     *
     * includeDeleted=true also returns soft-deleted employees — used by screens that
     * need to resolve a name/email for a historical record (attendance, leave,
     * regularization) even after the employee has been removed from the active
     * People list. getByIds() already ignores status, so this only affects the
     * SUPER_ADMIN branch below.
     */
    @GetMapping
    public ApiResponse<List<EmployeeResponse>> getAll(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        if (currentUser.isSuperAdmin(principal)) {
            return ApiResponse.ok(service.getAll(includeDeleted));
        }
        return ApiResponse.ok(service.getByIds(currentUser.accessibleEmployeeIds(principal)));
    }

    // Self-service: the currently logged-in user's own employee record.
    @GetMapping("/me")
    public ApiResponse<EmployeeResponse> me(@AuthenticationPrincipal CustomUserDetails principal) {
        UUID userId = principal.getUser().getId();
        return ApiResponse.ok(service.getByUserId(userId));
    }

    /**
     * Self-service: set MY OWN profile photo.
     *
     * Any authenticated employee may set their own photo. The employee is resolved
     * from the JWT (not from a client-supplied id), and ONLY the profile_photo_url
     * field is updated — nothing else on the record. Body: { "photoUrl": "/api/files/{id}" }.
     * The frontend uploads the image to /api/files first, then passes the returned url here.
     */
    @PutMapping("/me/photo")
    public ApiResponse<EmployeeResponse> updateMyPhoto(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody Map<String, String> body) {
        UUID userId = principal.getUser().getId();
        String photoUrl = body.get("photoUrl");
        return ApiResponse.ok("Profile photo updated",
                service.updateOwnProfilePhoto(userId, photoUrl));
    }

    /**
     * Fetch a single employee by id — only if the caller is allowed to see them
     * (self, a team member for a manager, or anyone for a super admin). Prevents
     * reading another employee's PII by guessing/leaking their id.
     */
    @GetMapping("/{id}")
    public ApiResponse<EmployeeResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, id);
        return ApiResponse.ok(service.getById(id));
    }

    /**
     * Prefill values for the Add-Employee form:
     *  - employeeCode: the next suggested Emp ID (TN0001, TN0002 …), shown as a default.
     *  - managerId / managerName: when a MANAGER opens the form, their own record — so
     *    the new hire defaults to reporting to them. A SUPER_ADMIN gets no default and
     *    picks the manager from the dropdown instead.
     */
    @GetMapping("/new-defaults")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<Map<String, Object>> newEmployeeDefaults(
            @AuthenticationPrincipal CustomUserDetails principal) {
        Map<String, Object> out = new HashMap<>();
        out.put("employeeCode", service.nextEmployeeCode());
        if (!currentUser.isSuperAdmin(principal)) {
            currentUser.ownEmployeeIdOrEmpty(principal).ifPresent(mid -> {
                EmployeeResponse me = service.getById(mid);
                out.put("managerId", mid);
                out.put("managerName", (me.firstName() + " " + me.lastName()).trim());
            });
        }
        return ApiResponse.ok(out);
    }

    /**
     * Create an employee. The reporting manager is resolved by role:
     *  - MANAGER (or HR-as-manager): forced to the creating manager (the "belonging manager"),
     *    so any hire added from the Manager Portal auto-joins that manager's team.
     *  - SUPER_ADMIN: uses the managerId selected on the request (may be null = unassigned).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<EmployeeResponse> create(
            @RequestBody EmployeeRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UUID managerToAssign = currentUser.isSuperAdmin(principal)
                ? body.managerId()
                : currentUser.ownEmployeeId(principal);
        return ApiResponse.ok("Created", service.create(body, managerToAssign));
    }

    /**
     * Update an employee — scoped: a MANAGER may only edit members of their own team
     * (or a hire they just created, which is auto-assigned to them). SUPER_ADMIN may
     * edit anyone. This closes the write-side gap left open in the Foundation batch.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<EmployeeResponse> update(
            @PathVariable UUID id,
            @RequestBody EmployeeRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, id);
        return ApiResponse.ok("Updated", service.update(id, body));
    }

    // Admin: link an employee record to a user account (enables that user's self-service).
    @PutMapping("/{id}/link-user/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<EmployeeResponse> linkUser(@PathVariable UUID id, @PathVariable UUID userId) {
        return ApiResponse.ok("Linked", service.linkUser(id, userId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}
