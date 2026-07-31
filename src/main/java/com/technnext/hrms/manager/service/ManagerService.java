package com.technnext.hrms.manager.service;

import com.technnext.hrms.auth.entity.User;
import com.technnext.hrms.auth.repository.UserRepository;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.employee.dto.EmployeeResponse;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.entity.EmployeeManager;
import com.technnext.hrms.employee.repository.EmployeeManagerRepository;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.manager.dto.AssignManagerRequest;
import com.technnext.hrms.manager.dto.ManagerTeamSize;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagerService {

    private final EmployeeManagerRepository managerRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    /**
     * Roles that make an employee eligible to be a "reporting manager" (HR =
     * Manager here). SUPER_ADMIN is included so a senior login — e.g. a Chief
     * Executive Manager's Super Admin — can be picked as a reporting manager
     * too, not just MANAGER/HR logins.
     */
    private static final Set<String> MANAGER_ROLE_NAMES =
            Set.of("MANAGER", "HR_ADMIN", "HR_EXECUTIVE", "SUPER_ADMIN");

    // ── Team queries ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EmployeeResponse> getTeam(UUID managerId) {
        return managerRepository.findByManagerId(managerId).stream()
                .map(EmployeeManager::getEmployeeId)
                .map(employeeRepository::findById)
                .filter(Optional::isPresent).map(Optional::get)
                .map(EmployeeResponse::from)
                .toList();
    }

    public List<UUID> getTeamIds(UUID managerId) {
        return managerRepository.findByManagerId(managerId).stream()
                .map(EmployeeManager::getEmployeeId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> getManagers(UUID employeeId) {
        return managerRepository.findByEmployeeId(employeeId).stream()
                .map(EmployeeManager::getManagerId)
                .map(employeeRepository::findById)
                .filter(Optional::isPresent).map(Optional::get)
                .map(EmployeeResponse::from)
                .toList();
    }

    /** Managers who ALREADY have at least one report (derived from links). */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getAllManagers() {
        return managerRepository.findAll().stream()
                .map(EmployeeManager::getManagerId)
                .distinct()
                .map(employeeRepository::findById)
                .filter(Optional::isPresent).map(Optional::get)
                .map(EmployeeResponse::from)
                .toList();
    }

    /**
     * Every employee ELIGIBLE to be a manager — i.e. whose linked login has a
     * MANAGER/HR role — whether or not they have reports yet. This is what the
     * Super Admin's "assign to manager" dropdown needs (getAllManagers is circular
     * for a brand-new manager with no team).
     */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getAssignableManagers() {
        Set<UUID> managerUserIds = userRepository.findAll().stream()
                .filter(u -> u.getRoles() != null && u.getRoles().stream()
                        .anyMatch(r -> MANAGER_ROLE_NAMES.contains(r.getName())))
                .map(User::getId)
                .collect(Collectors.toSet());

        return employeeRepository.findAll().stream()
                .filter(e -> e.getUserId() != null && managerUserIds.contains(e.getUserId()))
                .map(EmployeeResponse::from)
                .toList();
    }

    /**
     * All assignment rows (id + employeeId + managerId), so the admin screen can
     * display current teams and remove a specific assignment by id.
     */
    @Transactional(readOnly = true)
    public List<EmployeeManager> getAllAssignments() {
        return managerRepository.findAll();
    }

    /**
     * NEW: how many employees report to each manager — powers the org analytics
     * chart ("employees per manager"). Sorted largest team first.
     */
    @Transactional(readOnly = true)
    public List<ManagerTeamSize> getTeamSizes() {
        Map<UUID, Long> counts = managerRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        EmployeeManager::getManagerId,
                        Collectors.counting()));

        return counts.entrySet().stream()
                .map(e -> {
                    Employee emp = employeeRepository.findById(e.getKey()).orElse(null);
                    String name = emp != null
                            ? (safe(emp.getFirstName()) + " " + safe(emp.getLastName())).trim()
                            : "Unknown";
                    String code = emp != null ? emp.getEmployeeCode() : "—";
                    return new ManagerTeamSize(e.getKey(), name, code, e.getValue());
                })
                .sorted((a, b) -> Long.compare(b.teamSize(), a.teamSize()))
                .toList();
    }

    private String safe(String s) {
        return s != null ? s : "";
    }

    // ── Mutations ────────────────────────────────────────────────────────────────

    /** Assign an employee to a manager (one manager per employee). */
    @Transactional
    public EmployeeManager assign(AssignManagerRequest req) {
        if (!employeeRepository.existsById(req.employeeId()))
            throw new ResourceNotFoundException("Employee", req.employeeId());
        if (!employeeRepository.existsById(req.managerId()))
            throw new ResourceNotFoundException("Manager", req.managerId());
        if (req.employeeId().equals(req.managerId()))
            throw new com.technnext.hrms.common.exception.BadRequestException(
                    "An employee cannot be their own manager.");

        List<EmployeeManager> existing = managerRepository.findByEmployeeId(req.employeeId());
        if (!existing.isEmpty()) managerRepository.deleteAll(existing);

        EmployeeManager em = EmployeeManager.builder()
                .employeeId(req.employeeId())
                .managerId(req.managerId())
                .isPrimary(req.isPrimary() != null ? req.isPrimary() : true)
                .effectiveFrom(req.effectiveFrom() != null ? req.effectiveFrom() : LocalDate.now())
                .build();
        return managerRepository.save(em);
    }

    public void unassign(Integer id) {
        if (!managerRepository.existsById(id))
            throw new ResourceNotFoundException("EmployeeManager", id);
        managerRepository.deleteById(id);
    }
}