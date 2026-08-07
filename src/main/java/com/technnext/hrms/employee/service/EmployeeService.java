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
import com.technnext.hrms.email.EmployeeWelcomeEmailEvent;
import com.technnext.hrms.invite.dto.InviteEmployeeRequest;
import com.technnext.hrms.invite.dto.OnboardingCompleteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final com.technnext.hrms.employee.repository.EmployeeContactRepository employeeContactRepository;
    private final ProbationTrackingRepository probationTrackingRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
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
                    resp.dateOfJoining(), resp.employmentType(), resp.status(), resp.onboardingStatus(),
                    resp.branchId(), resp.branchName(), resp.departmentId(), resp.departmentName(),
                    resp.designationId(), resp.designationName(), resp.shiftId(), resp.shiftName(),
                    resp.probationEndDate(), resp.confirmationDate(),
                    resp.isFresher(), resp.aadhaarNumber(), resp.panNumber(), resp.bankAccountNumber(),
                    resp.bankName(), resp.ifscCode(), resp.uanNumber(),
                    resp.email(), resp.userId(), tempPassword, resp.profilePhotoUrl(),
                    resp.reportingManagerId(), resp.reportingManagerName(),
                    resp.contact(),
                    resp.education(), resp.experience());
        }

        // Trigger the "welcome to the company" email whenever an email address was
        // entered on the Add-Employee form — regardless of whether a login account
        // was also created. Publishing here (inside the transactional method) is
        // safe: the listener is AFTER_COMMIT + @Async, so the email only goes out
        // once this employee is durably saved, and a slow/broken mail server can
        // never delay or fail this request.
        if (req.email() != null && !req.email().isBlank()) {
            eventPublisher.publishEvent(new EmployeeWelcomeEmailEvent(
                    req.email().trim(),
                    saved.getFirstName(),
                    saved.getLastName(),
                    saved.getEmployeeCode(),
                    saved.getDesignation() != null ? saved.getDesignation().getName() : null,
                    saved.getDepartment() != null ? saved.getDepartment().getName() : null,
                    tempPassword != null ? req.email().trim() : null,
                    tempPassword
            ));
        }
        return resp;
    }

    /**
     * Update an employee.
     *
     * @param isAdmin true for SUPER_ADMIN (and HR roles, if ever used) — the only
     *                callers allowed to change an existing Employee ID. A MANAGER
     *                editing their team can update every other field, but any
     *                attempt to change employeeCode is rejected here, even if the
     *                frontend field were somehow re-enabled or the API called
     *                directly — this is a server-side guarantee, not just a UI lock.
     */
    @Transactional
    public EmployeeResponse update(UUID id, EmployeeRequest req, boolean isAdmin) {
        Employee e = findOrThrow(id);
        boolean codeChanged = req.employeeCode() != null
                && !req.employeeCode().equals(e.getEmployeeCode());
        if (codeChanged && !isAdmin) {
            throw new BadRequestException(
                    "Employee ID cannot be changed. Only a Super Admin can update it.");
        }
        if (codeChanged && employeeRepository.existsByEmployeeCode(req.employeeCode())) {
            throw new BadRequestException("Employee code already exists: " + req.employeeCode());
        }
        apply(e, req, isAdmin);
        Employee saved = employeeRepository.save(e);
        educationRepository.deleteByEmployeeId(id);
        experienceRepository.deleteByEmployeeId(id);
        saveChildren(id, req);
        return toResponse(saved);
    }

    // =========================================================================
    // INVITE EMPLOYEE / SELF-ONBOARDING
    // =========================================================================

    /**
     * Super Admin's "Send Invitation": creates a minimal employee shell —
     * NO personal/statutory/bank data, NO login account yet — and marks it
     * onboardingStatus=INVITED. The candidate fills in the rest themselves via
     * the emailed onboarding link (see EmployeeInviteService.completeOnboarding).
     *
     * Deliberately does NOT touch leave accrual or probation tracking here —
     * those are created once onboarding actually completes, so an invite that
     * expires unused never leaves half-created HR records behind.
     */
    @Transactional
    public Employee createInviteShell(InviteEmployeeRequest req) {
        String code = (req.employeeCode() == null || req.employeeCode().isBlank())
                ? generateEmployeeCode()
                : req.employeeCode().trim();
        if (employeeRepository.existsByEmployeeCode(code)) {
            throw new BadRequestException("Employee code already exists: " + code);
        }
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFirstName(req.firstName());
        e.setLastName(req.lastName());
        e.setDateOfJoining(req.dateOfJoining());
        e.setEmail(req.email());
        e.setOnboardingStatus("INVITED");
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

        Employee saved = employeeRepository.save(e);
        assignManagerIfPresent(saved.getId(), req.managerId());
        return saved;
    }

    /**
     * Candidate's "Complete Registration": fills in everything the invite shell
     * didn't have, and (only now) sets up leave accrual + probation tracking,
     * same as the classic create() flow does at creation time. Does NOT touch
     * onboardingStatus or create the login account — that's orchestrated by
     * EmployeeInviteService, which calls this, then AuthService, then flips
     * onboardingStatus to ACTIVE once everything has succeeded.
     */
    @Transactional
    public Employee applyOnboardingData(UUID employeeId, OnboardingCompleteRequest req) {
        Employee e = findOrThrow(employeeId);

        e.setDateOfBirth(req.dateOfBirth());
        e.setGender(req.gender());
        e.setBloodGroup(req.bloodGroup());
        e.setMaritalStatus(req.maritalStatus());
        e.setNationality(req.nationality());
        e.setAadhaarNumber(req.aadhaarNumber());
        e.setPanNumber(req.panNumber());
        e.setBankAccountNumber(req.bankAccountNumber());
        e.setBankName(req.bankName());
        e.setIfscCode(req.ifscCode());
        e.setUanNumber(req.uanNumber());
        e.setIsFresher(req.isFresher() == null ? Boolean.TRUE : req.isFresher());

        Employee saved = employeeRepository.save(e);

        // Address + emergency contact live in employee_contacts, not on the
        // employee record itself — upsert (there's at most one row per employee).
        com.technnext.hrms.employee.entity.EmployeeContact contact = employeeContactRepository
                .findByEmployeeId(employeeId)
                .orElseGet(() -> com.technnext.hrms.employee.entity.EmployeeContact.builder()
                        .employeeId(employeeId).build());
        contact.setAddressLine1(req.addressLine1());
        contact.setAddressLine2(req.addressLine2());
        contact.setCity(req.city());
        contact.setState(req.state());
        contact.setPincode(req.postalCode());
        contact.setCountry(req.country());
        contact.setPermAddressLine1(req.permAddressLine1());
        contact.setPermAddressLine2(req.permAddressLine2());
        contact.setPermCity(req.permCity());
        contact.setPermState(req.permState());
        contact.setPermPincode(req.permPincode());
        contact.setEmergencyName(req.emergencyContactName());
        contact.setEmergencyPhone(req.emergencyContactPhone());
        contact.setEmergencyRelation(req.emergencyContactRelation());
        employeeContactRepository.save(contact);

        boolean fresher = Boolean.TRUE.equals(saved.getIsFresher());
        saveChildren(employeeId, req.education(), fresher ? null : req.experience());

        leaveBalanceService.accrueForNewEmployee(saved.getId(), saved.getDateOfJoining(), saved.getGender());

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
        return saved;
    }

    /** Flip an employee from INVITED to ACTIVE once onboarding + login creation both succeed. */
    @Transactional
    public void markOnboardingComplete(UUID employeeId, UUID newUserId) {
        Employee e = findOrThrow(employeeId);
        e.setOnboardingStatus("ACTIVE");
        e.setUserId(newUserId);
        employeeRepository.save(e);
    }

    @Transactional(readOnly = true)
    public Employee getEntityById(UUID id) {
        return findOrThrow(id);
    }

    /** Used by onboarding (candidate has no userId yet, so updateOwnProfilePhoto()
     *  by userId doesn't apply — this sets it directly by employeeId instead). */
    @Transactional
    public void setProfilePhotoUrl(UUID employeeId, String url) {
        Employee e = findOrThrow(employeeId);
        e.setProfilePhotoUrl(url);
        employeeRepository.save(e);
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
        // Base the next number on the HIGHEST employee-code number that has ever
        // existed (active or soft-deleted), not on employeeRepository.count().
        // count() drifts from the true max whenever there are gaps in the
        // sequence (e.g. historical seed data) or soft-deleted rows — that drift
        // was previously causing the next code to jump further ahead than
        // expected. Deleted employees' codes are intentionally never reused
        // (their letters/payroll/audit history may still reference that code).
        long maxNum = 0;
        for (Employee e : employeeRepository.findAll()) {
            String code = e.getEmployeeCode();
            if (code == null) continue;
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("(\\d+)\\s*$").matcher(code.trim());
            if (m.find()) {
                try {
                    long num = Long.parseLong(m.group(1));
                    if (num > maxNum) maxNum = num;
                } catch (NumberFormatException ignored) {
                    // non-numeric suffix (e.g. a manually entered code) — skip it
                }
            }
        }
        long n = maxNum + 1;
        for (int i = 0; i < 10_000; i++) {
            // "TN 001", "TN 002", ... — prefix, space, 3-digit zero-padded number.
            String candidate = EMP_CODE_PREFIX + " " + String.format("%03d", n);
            if (!employeeRepository.existsByEmployeeCode(candidate)) return candidate;
            n++;
        }
        return EMP_CODE_PREFIX + " " + System.currentTimeMillis();
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

        com.technnext.hrms.employee.dto.EmployeeContactDto contact = employeeContactRepository
                .findByEmployeeId(e.getId())
                .map(com.technnext.hrms.employee.dto.EmployeeContactDto::from)
                .orElse(null);

        return EmployeeResponse.from(e, edu, exp, reportingManagerId, reportingManagerName, contact);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void saveChildren(UUID employeeId, EmployeeRequest req) {
        boolean fresher = req.isFresher() == null || req.isFresher();
        saveChildren(employeeId, req.education(), fresher ? null : req.experience());
    }

    /**
     * Shared by both the classic Add Employee flow (via the overload above) and
     * EmployeeInviteService.completeOnboarding() — same persistence logic either
     * way, just sourced from different request DTOs.
     */
    public void saveChildren(UUID employeeId, List<EducationDto> education, List<ExperienceDto> experience) {
        if (education != null) {
            for (EducationDto d : education) {
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
        if (experience != null) {
            for (ExperienceDto x : experience) {
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
        // Used only by create(): the caller-supplied/generated code is set (and then
        // overwritten with the final resolved `code`) regardless of role, since a
        // brand-new employee obviously has no prior Employee ID to protect.
        apply(e, req, true);
    }

    private void apply(Employee e, EmployeeRequest req, boolean isAdmin) {
        if (isAdmin) {
            e.setEmployeeCode(req.employeeCode());
        }
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