package com.technnext.hrms.user.controller;

import com.technnext.hrms.auth.entity.Role;
import com.technnext.hrms.auth.entity.User;
import com.technnext.hrms.auth.repository.RoleRepository;
import com.technnext.hrms.auth.repository.UserRepository;
import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.entity.EmployeeManager;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.employee.repository.EmployeeManagerRepository;
import com.technnext.hrms.user.dto.UserDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeManagerRepository employeeManagerRepository;
    private final RoleRepository roleRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private UserDto toDto(User u) {
        List<String> roleNames = u.getRoles() == null ? List.of()
                : u.getRoles().stream().map(Role::getName).toList();
        return new UserDto(u.getId(), u.getEmail(), roleNames,
                u.getIsActive(), u.getLastLogin());
    }

    // Active users
    @GetMapping
    public ApiResponse<List<UserDto>> listActive() {
        return ApiResponse.ok(userRepository.findByIsActive(true).stream().map(this::toDto).toList());
    }

    // Deactivated users
    @GetMapping("/deleted")
    public ApiResponse<List<UserDto>> listDeleted() {
        return ApiResponse.ok(userRepository.findByIsActive(false).stream().map(this::toDto).toList());
    }

    /**
     * Change a user's role (e.g. EMPLOYEE -> MANAGER). Replaces all existing
     * roles with the single new role. Super admin only.
     * Body: { "roleName": "MANAGER" }
     */
    @PutMapping("/{id}/role")
    @Transactional
    public ApiResponse<UserDto> changeRole(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String roleName = body.get("roleName");
        if (roleName == null || roleName.isBlank()) {
            throw new com.technnext.hrms.common.exception.BadRequestException("roleName is required");
        }
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleName));
        // Use a mutable set; replacing a @ManyToMany with an immutable Set.of(...)
        // can cause Hibernate to fail when it tries to manage the collection.
        Set<Role> newRoles = new HashSet<>();
        newRoles.add(role);
        u.setRoles(newRoles);
        userRepository.save(u);

        // When someone is demoted from MANAGER to a non-manager role, they can
        // no longer have a team. Clear the reporting links of anyone who was
        // reporting to them, so those employees show no reporting manager until
        // they are reassigned to a new manager.
        if (!"MANAGER".equalsIgnoreCase(roleName)) {
            Optional<Employee> demoted = employeeRepository.findByUserId(id);
            demoted.ifPresent(emp -> {
                List<EmployeeManager> team =
                        employeeManagerRepository.findByManagerId(emp.getId());
                if (!team.isEmpty()) {
                    employeeManagerRepository.deleteAll(team);
                }
            });
        }

        return ApiResponse.ok("Role updated to " + roleName, toDto(u));
    }

    // PERMANENT (hard) delete of the LOGIN. This still failed after the previous
    // fix (unlinking the employee) because several OTHER tables also point at
    // users.id whenever this person approved/reviewed/uploaded something:
    // leave approvals, attendance-regularization reviews, generated letters,
    // letter templates, uploaded documents/files, audit log entries, HR-ops
    // records (confirmation/exit/probation/onboarding). None of those rows
    // belong to the user being deleted — they belong to whatever they acted on
    // — so we don't delete them, we just clear the "who did this" pointer
    // (set to NULL) and keep the historical row itself. The only rows that
    // truly belong to the user (their own notifications) are removed outright.
    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> deletePermanent(@PathVariable UUID id) {
        User u = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));

        employeeRepository.findByUserId(id).ifPresent(emp -> {
            emp.setUserId(null);
            employeeRepository.save(emp);
        });

        clearHistoricalReferences(id);

        userRepository.delete(u);
        return ApiResponse.ok("User permanently deleted", null);
    }

    private void clearHistoricalReferences(UUID userId) {
        // Nullable "actor" columns — keep the row, clear the pointer.
        entityManager.createNativeQuery(
                "UPDATE attendance_regularizations SET reviewed_by = NULL WHERE reviewed_by = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE leave_requests SET approved_by = NULL WHERE approved_by = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE generated_letters SET generated_by = NULL WHERE generated_by = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE letter_templates SET created_by = NULL WHERE created_by = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE employee_documents SET uploaded_by = NULL WHERE uploaded_by = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE file_storage SET uploaded_by = NULL WHERE uploaded_by = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE audit_logs SET user_id = NULL WHERE user_id = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE confirmation_records SET confirmed_by = NULL WHERE confirmed_by = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE exit_records SET processed_by = NULL WHERE processed_by = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE probation_tracking SET reviewed_by = NULL WHERE reviewed_by = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE onboarding_checklists SET completed_by = NULL WHERE completed_by = :id")
                .setParameter("id", userId).executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE tickets SET resolved_by_id = NULL WHERE resolved_by_id = :id")
                .setParameter("id", userId).executeUpdate();

        // Rows that actually belong to the user (not nullable / not historical
        // "who approved this" pointers) — safe to remove outright.
        entityManager.createNativeQuery("DELETE FROM notifications WHERE user_id = :id")
                .setParameter("id", userId).executeUpdate();
    }

    @PostMapping("/{id}/deactivate")
    @Transactional
    public ApiResponse<Void> deactivate(@PathVariable UUID id) {
        User u = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        u.setIsActive(false);
        userRepository.save(u);
        return ApiResponse.ok("User deactivated", null);
    }

    @PostMapping("/{id}/restore")
    @Transactional
    public ApiResponse<Void> restore(@PathVariable UUID id) {
        User u = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        u.setIsActive(true);
        userRepository.save(u);
        return ApiResponse.ok("User restored", null);
    }
}
