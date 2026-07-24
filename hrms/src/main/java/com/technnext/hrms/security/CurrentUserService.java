package com.technnext.hrms.security;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.employee.repository.EmployeeManagerRepository;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Single source of truth for "who can this logged-in user see / act for?".
 *
 * HIERARCHY:
 *   SUPER_ADMIN  -> everyone
 *   MANAGER (=HR)-> their own team + themselves
 *   EMPLOYEE     -> themselves only
 *
 * HARDENING: a user without a linked employee record (e.g. a bootstrap Super Admin
 * account) must NOT crash the whole app. Access checks for a Super Admin never
 * require their own employee id, and ownEmployeeId() is only demanded where it is
 * genuinely needed (non-admin self-service).
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeManagerRepository employeeManagerRepository;

    // ── role checks ──────────────────────────────────────────────────────────────

    public boolean isSuperAdmin(CustomUserDetails principal) {
        return hasRole(principal, "ROLE_SUPER_ADMIN");
    }

    public boolean isManager(CustomUserDetails principal) {
        return hasRole(principal, "ROLE_MANAGER")
                || hasRole(principal, "ROLE_HR_ADMIN")
                || hasRole(principal, "ROLE_HR_EXECUTIVE");
    }

    private boolean hasRole(CustomUserDetails principal, String role) {
        if (principal == null) return false;
        return principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    // ── identity ─────────────────────────────────────────────────────────────────

    /** The caller's own employee id, or empty if their login has no employee record. */
    public Optional<UUID> ownEmployeeIdOrEmpty(CustomUserDetails principal) {
        UUID userId = principal.getUser().getId();
        return employeeRepository.findByUserId(userId).map(e -> e.getId());
    }

    /**
     * The caller's own employee id — REQUIRED. Throws a clear message if their login
     * isn't linked to an employee (used for non-admin self-service actions).
     */
    public UUID ownEmployeeId(CustomUserDetails principal) {
        return ownEmployeeIdOrEmpty(principal)
                .orElseThrow(() -> new BadRequestException(
                        "No employee profile is linked to your account. Contact your administrator."));
    }

    /** Employee ids on this manager's team. */
    public Set<UUID> teamEmployeeIds(UUID managerEmployeeId) {
        return employeeManagerRepository.findByManagerId(managerEmployeeId).stream()
                .map(em -> em.getEmployeeId())
                .collect(Collectors.toSet());
    }

    // ── scope ───────────────────────────────────────────────────────────────────

    public Set<UUID> accessibleEmployeeIds(CustomUserDetails principal) {
        if (isSuperAdmin(principal)) {
            return employeeRepository.findAll().stream()
                    .map(e -> e.getId())
                    .collect(Collectors.toSet());
        }
        Set<UUID> ids = new HashSet<>();
        Optional<UUID> own = ownEmployeeIdOrEmpty(principal);
        own.ifPresent(ids::add);
        if (isManager(principal)) {
            own.ifPresent(mid -> ids.addAll(teamEmployeeIds(mid)));
        }
        return ids;
    }

    public boolean canAccessEmployee(CustomUserDetails principal, UUID employeeId) {
        if (isSuperAdmin(principal)) return true;                 // no own-employee needed
        Optional<UUID> own = ownEmployeeIdOrEmpty(principal);
        if (own.isEmpty()) return false;                          // not admin, no profile -> no access
        if (own.get().equals(employeeId)) return true;
        return isManager(principal) && teamEmployeeIds(own.get()).contains(employeeId);
    }

    public void assertCanAccessEmployee(CustomUserDetails principal, UUID employeeId) {
        if (!canAccessEmployee(principal, employeeId)) {
            throw new BadRequestException("You do not have access to this employee's records.");
        }
    }

    /**
     * For WRITE self-service actions (check-in, regularization, leave apply).
     *  - Super Admin: acts for the requested employee; if none supplied, uses their own
     *    (and if they have no employee record, asks them to pick one explicitly rather
     *    than throwing an opaque error).
     *  - Manager: a team member or themselves.
     *  - Employee: themselves only.
     */
    public UUID resolveActingEmployeeId(CustomUserDetails principal, UUID requestedEmployeeId) {
        if (isSuperAdmin(principal)) {
            if (requestedEmployeeId != null) return requestedEmployeeId;
            return ownEmployeeIdOrEmpty(principal).orElseThrow(() -> new BadRequestException(
                    "Select an employee to perform this action for."));
        }
        UUID own = ownEmployeeId(principal);
        if (requestedEmployeeId == null || requestedEmployeeId.equals(own)) {
            return own;
        }
        if (isManager(principal) && teamEmployeeIds(own).contains(requestedEmployeeId)) {
            return requestedEmployeeId;
        }
        throw new BadRequestException("You can only perform this action for yourself or your team.");
    }
}