package com.technnext.hrms.employee.service;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.employee.dto.*;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.entity.EmployeeEducation;
import com.technnext.hrms.employee.entity.EmployeeExperience;
import com.technnext.hrms.employee.entity.EmployeeManager;
import com.technnext.hrms.employee.repository.EmployeeEducationRepository;
import com.technnext.hrms.employee.repository.EmployeeExperienceRepository;
import com.technnext.hrms.employee.repository.EmployeeManagerRepository;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.organization.repository.BranchRepository;
import com.technnext.hrms.organization.repository.DepartmentRepository;
import com.technnext.hrms.organization.repository.DesignationRepository;
import com.technnext.hrms.organization.repository.ShiftRepository;
import com.technnext.hrms.hrops.entity.ProbationTracking;
import com.technnext.hrms.hrops.repository.ProbationTrackingRepository;
import com.technnext.hrms.leave.service.LeaveBalanceService;
import com.technnext.hrms.auth.service.AuthService;
import com.technnext.hrms.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final ShiftRepository shiftRepository;
    private final EmployeeEducationRepository educationRepository;
    private final EmployeeExperienceRepository experienceRepository;
    private final EmployeeManagerRepository employeeManagerRepository;
    private final ProbationTrackingRepository probationTrackingRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final AuthService authService;
    private final UserRepository userRepository;
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getAll() {
        return getAll(false);
    }

    /**
     * @param includeDeleted when true, soft-deleted employees (status DELETED) are
     *                       included too. Used by directory/lookup callers (e.g.
     *                       resolving the employee name+email on historical
     *                       attendance/leave/regularization records) so a deleted
     *                       employee doesn't show as a bare UUID. The main People
     *                       list still calls getAll() (false) so deleted employees
     *                       stay hidden from that screen.
     */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getAll(boolean includeDeleted) {
        return employeeRepository.findAll().stream()
                .filter(e -> includeDeleted || !"DELETED".equalsIgnoreCase(e.getStatus()))
                .map(this::toResponse)
                .toList();
    }
    @Transactional(readOnly = true)
    public EmployeeResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }
    /**
     * Scoped list: return ONLY the employees whose ids are supplied. Used by the
     * controller together with CurrentUserService.accessibleEmployeeIds() so that
     * a MANAGER sees just their team and an EMPLOYEE sees just themselves, while a
     * SUPER_ADMIN uses getAll(). Never returns the whole table.
     */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getByIds(java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return employeeRepository.findAllById(ids).stream().map(this::toResponse).toList();
    }

    /**
     * Create an employee (and optionally a linked login account).
     */
    @Transactional
    public EmployeeResponse create(EmployeeRequest req) {
        // Backward-compatible entry point: honour any managerId carried on the request.
        return create(req, req.managerId());
    }

    /**
     * Create an employee, optionally auto-assigning them to a manager.
     */
    @Transactional
    public EmployeeResponse create(EmployeeRequest req, UUID managerIdToAssign) {
        String code = (req.employeeCode() == null || req.employeeCode().isBlank())
                ? generateEmployeeCode()
                : req.employeeCode().trim();
        if (employeeRepository.existsByEmployeeCode(code)) {
            throw new BadRequestException("Employee code already exists: " + code);
        }
        Employee e = new Employee();
        apply(e, req);
        e.setEmployeeCode(code); // the (possibly generated) code always wins
        Employee saved = employeeRepository.save(e);
        saveChildren(saved.getId(), req);
        leaveBalanceService.accrueForNewEmployee(saved.getId(), saved.getDateOfJoining(), saved.getGender());

        // Option A: auto-create the probation record from the joining date so HR
        // doesn't re-enter it. Start = date of joining; end = the employee's
        // probation end date if set, else joining date + 6 months.
        LocalDate doj = saved.getDateOfJoining();
        if (doj != null) {
            LocalDate probEnd = saved.getProbationEndDate() != null
                    ? saved.getProbationEndDate()
                    : doj.plusMonths(6);
            probationTrackingRepository.save(ProbationTracking.builder()
                    .employeeId(saved.getId())
                    .probationStart(doj)
                    .probationEnd(probEnd)
                    .status("IN_PROGRESS")
                    .build());
        }

        // Manager Portal: link the new hire to their manager (one manager per employee).
        assignManagerIfPresent(saved.getId(), managerIdToAssign);

        String tempPassword = null;
        if (req.email() != null && !req.email().isBlank()
                && req.loginRole() != null && !req.loginRole().isBlank()) {
            Map<String, Object> acct = authService.createUserAccount(
                req.email(), req.loginRole(), req.password());
            UUID newUserId = (UUID) acct.get("userId");
            tempPassword = (String) acct.get("tempPassword");
            saved.setUserId(newUserId);
            employeeRepository.save(saved);
        }
        EmployeeResponse resp = toResponse(saved);
        if (tempPassword != null) {
            resp = new EmployeeResponse(
                    resp.id(), resp.employeeCode(), resp.firstName(), resp.lastName(), resp.middleName(),
                    resp.dateOfBirth(), resp.gender(), resp.bloodGroup(), resp.maritalStatus(), resp.nationality(),
                    resp.dateOfJoining(), resp.employmentType(), resp.status(),
                    resp.branchId(), resp.branchName(), resp.departmentId(), resp.departmentName(),
                    resp.designationId(), resp.designationName(), resp.shiftId(), resp.shiftName(),
                    resp.probationEndDate(), resp.confirmationDate(),
                    resp.isFresher(), resp.aadhaarNumber(), resp.panNumber(), resp.bankAccountNumber(),
                    resp.bankName(), resp.ifscCode(), resp.uanNumber(),
                    resp.email(), resp.userId(), tempPassword, resp.profilePhotoUrl(),
                    resp.reportingManagerId(), resp.reportingManagerName(),
                    resp.education(), resp.experience());
        }
        return resp;
    }

    @Transactional
    public EmployeeResponse update(UUID id, EmployeeRequest req) {
        Employee e = findOrThrow(id);
        if (req.employeeCode() != null
                && !req.employeeCode().equals(e.getEmployeeCode())
                && employeeRepository.existsByEmployeeCode(req.employeeCode())) {
            throw new BadRequestException("Employee code already exists: " + req.employeeCode());
        }
        apply(e, req);
        Employee saved = employeeRepository.save(e);
        educationRepository.deleteByEmployeeId(id);
        experienceRepository.deleteByEmployeeId(id);
        saveChildren(id, req);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        // Soft-delete: archive the employee and disable their login instead of
        // physically removing rows. This preserves history (attendance, letters,
        // audit trail) and avoids foreign-key constraint failures. The employee
        // is hidden from the list via the status filter in getAll().
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
        e.setStatus("DELETED");
        employeeRepository.save(e);

        // Disable the linked login so the person can no longer sign in.
        UUID linkedUserId = e.getUserId();
        if (linkedUserId != null) {
            userRepository.findById(linkedUserId).ifPresent(u -> {
                u.setIsActive(false);
                userRepository.save(u);
            });
        }
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getByUserId(UUID userId) {
        Employee e = employeeRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee for user", userId));
        return toResponse(e);
    }

    @Transactional
    public EmployeeResponse linkUser(UUID employeeId, UUID userId) {
        Employee e = findOrThrow(employeeId);
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        e.setUserId(userId);
        return toResponse(employeeRepository.save(e));
    }

    // ---------- helpers ----------

    private Employee findOrThrow(UUID id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    // Manager Portal helpers

    private static final String EMP_CODE_PREFIX = "TN";

    @Transactional(readOnly = true)
    public String nextEmployeeCode() {
        return generateEmployeeCode();
    }

    private String generateEmployeeCode() {
        long n = employeeRepository.count() + 1;
        for (int i = 0; i < 10_000; i++) {
            String candidate = EMP_CODE_PREFIX + String.format("%04d", n);
            if (!employeeRepository.existsByEmployeeCode(candidate)) return candidate;
            n++;
        }
        return EMP_CODE_PREFIX + System.currentTimeMillis();
    }

    private void assignManagerIfPresent(UUID employeeId, UUID managerId) {
        if (managerId == null) return;
        if (managerId.equals(employeeId)) {
            throw new BadRequestException("An employee cannot be their own manager.");
        }
        if (!employeeRepository.existsById(managerId)) {
            throw new ResourceNotFoundException("Manager", managerId);
        }
        List<EmployeeManager> existing = employeeManagerRepository.findByEmployeeId(employeeId);
        if (!existing.isEmpty()) employeeManagerRepository.deleteAll(existing);
        employeeManagerRepository.save(EmployeeManager.builder()
                .employeeId(employeeId)
                .managerId(managerId)
                .isPrimary(true)
                .effectiveFrom(LocalDate.now())
                .build());
    }

    private EmployeeResponse toResponse(Employee e) {
        List<EducationDto> edu = educationRepository.findByEmployeeId(e.getId()).stream()
                .map(x -> new EducationDto(x.getId(), x.getLevel(), x.getInstitution(), x.getSpecialization(),
                        x.getPercentage(), x.getFromMonth(), x.getFromYear(), x.getToMonth(), x.getToYear(),
                        x.getDocumentUrl()))
                .toList();
        List<ExperienceDto> exp = experienceRepository.findByEmployeeId(e.getId()).stream()
                .map(x -> new ExperienceDto(x.getId(), x.getCompany(), x.getDesignation(),
                        x.getFromMonth(), x.getFromYear(), x.getToMonth(), x.getToYear()))
                .toList();

        // Resolve the reporting manager (one primary manager per employee).
        UUID reportingManagerId = null;
        String reportingManagerName = null;
        List<EmployeeManager> links = employeeManagerRepository.findByEmployeeId(e.getId());
        if (!links.isEmpty()) {
            reportingManagerId = links.get(0).getManagerId();
            final UUID mid = reportingManagerId;
            reportingManagerName = employeeRepository.findById(mid)
                    .map(m -> (safe(m.getFirstName()) + " " + safe(m.getLastName())).trim())
                    .orElse(null);
        }

        return EmployeeResponse.from(e, edu, exp, reportingManagerId, reportingManagerName);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void saveChildren(UUID employeeId, EmployeeRequest req) {
        if (req.education() != null) {
            for (EducationDto d : req.education()) {
                if (d == null || d.level() == null || d.level().isBlank()) continue;
                educationRepository.save(EmployeeEducation.builder()
                        .employeeId(employeeId)
                        .level(d.level())
                        .institution(d.institution())
                        .specialization(d.specialization())
                        .percentage(d.percentage())
                        .fromMonth(d.fromMonth()).fromYear(d.fromYear())
                        .toMonth(d.toMonth()).toYear(d.toYear())
                        .documentUrl(d.documentUrl())
                        .build());
            }
        }
        boolean fresher = req.isFresher() == null || req.isFresher();
        if (!fresher && req.experience() != null) {
            for (ExperienceDto x : req.experience()) {
                if (x == null || x.company() == null || x.company().isBlank()) continue;
                experienceRepository.save(EmployeeExperience.builder()
                        .employeeId(employeeId)
                        .company(x.company())
                        .designation(x.designation())
                        .fromMonth(x.fromMonth()).fromYear(x.fromYear())
                        .toMonth(x.toMonth()).toYear(x.toYear())
                        .build());
            }
        }
    }

    @Transactional
    public EmployeeResponse updateOwnProfilePhoto(UUID userId, String photoUrl) {
        Employee emp = employeeRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException(
                        "No employee profile is linked to your account."));
        emp.setProfilePhotoUrl(photoUrl);
        return toResponse(employeeRepository.save(emp));
    }

    private void apply(Employee e, EmployeeRequest req) {
        e.setEmployeeCode(req.employeeCode());
        e.setFirstName(req.firstName());
        e.setLastName(req.lastName());
        e.setMiddleName(req.middleName());
        e.setDateOfBirth(req.dateOfBirth());
        e.setGender(req.gender());
        e.setBloodGroup(req.bloodGroup());
        e.setMaritalStatus(req.maritalStatus());
        e.setNationality(req.nationality());
        e.setDateOfJoining(req.dateOfJoining());
        if (req.employmentType() != null) e.setEmploymentType(req.employmentType());
        if (req.status() != null) e.setStatus(req.status());
        e.setProbationEndDate(req.probationEndDate());
        e.setConfirmationDate(req.confirmationDate());

        e.setIsFresher(req.isFresher() == null ? Boolean.TRUE : req.isFresher());
        e.setAadhaarNumber(req.aadhaarNumber());
        e.setPanNumber(req.panNumber());
        e.setBankAccountNumber(req.bankAccountNumber());
        e.setBankName(req.bankName());
        e.setIfscCode(req.ifscCode());
        e.setUanNumber(req.uanNumber());
        e.setEmail(req.email());

        e.setBranch(req.branchId() == null ? null :
                branchRepository.findById(req.branchId())
                        .orElseThrow(() -> new ResourceNotFoundException("Branch", req.branchId())));
        e.setDepartment(req.departmentId() == null ? null :
                departmentRepository.findById(req.departmentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Department", req.departmentId())));
        e.setDesignation(req.designationId() == null ? null :
                designationRepository.findById(req.designationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Designation", req.designationId())));
        e.setShift(req.shiftId() == null ? null :
                shiftRepository.findById(req.shiftId())
                        .orElseThrow(() -> new ResourceNotFoundException("Shift", req.shiftId())));
    }
}
