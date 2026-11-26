package com.technnext.hrms.suite.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.suite.dto.SuiteRecordRequest;
import com.technnext.hrms.suite.entity.SuiteRecord;
import com.technnext.hrms.suite.repository.SuiteRecordRepository;
import com.technnext.hrms.suite.dto.PayrollRunRequest;
import com.technnext.hrms.suite.dto.TimesheetGenerateRequest;
import com.technnext.hrms.suite.service.PayrollRunService;
import com.technnext.hrms.suite.service.PayrollArchiveService;
import com.technnext.hrms.suite.service.TimesheetService;
import com.technnext.hrms.suite.entity.PayrollProfile;
import com.technnext.hrms.suite.dto.PayrollProfileRequest;
import com.technnext.hrms.suite.repository.PayrollProfileRepository;
import com.technnext.hrms.suite.service.PayslipPdfService;
import com.technnext.hrms.suite.service.PayslipPdfService.PayslipDocument;
import com.technnext.hrms.email.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Role- and employee-scoped API shared by payroll, time, work, performance and
 * asset workstreams. Computer monitoring is intentionally consent-led and stores
 * no screenshots, keystrokes, URLs, or application telemetry. */
@RestController
@RequestMapping("/api/hr-suite")
@RequiredArgsConstructor
public class SuiteController {
    private static final Set<String> MODULES = Set.of("payroll", "payslips", "statutory", "time-tracking", "tasks", "timesheets", "performance", "assets", "monitoring");
    private final SuiteRecordRepository repository;
    private final EmployeeRepository employees;
    private final CurrentUserService currentUser;
    private final PayrollRunService payrollRunService;
    private final PayrollArchiveService payrollArchiveService;
    private final TimesheetService timesheetService;
    private final PayrollProfileRepository payrollProfiles;
    private final PayslipPdfService payslipPdfService;
    private final EmailService emailService;

    public record EmployeeOption(UUID id, String employeeCode, String displayName) { }

    @GetMapping("/employee-options")
    public ApiResponse<List<EmployeeOption>> employeeOptions(@AuthenticationPrincipal CustomUserDetails user) {
        var rows = currentUser.canManagePayroll(user)
                ? employees.findAll()
                : employees.findAllById(currentUser.accessibleEmployeeIds(user));
        return ApiResponse.ok(rows.stream().map(e -> new EmployeeOption(e.getId(), e.getEmployeeCode(),
                (e.getFirstName() + " " + e.getLastName()).trim())).toList());
    }

    @PostMapping("/payroll/run")
    public ApiResponse<List<SuiteRecord>> runPayroll(@Valid @RequestBody PayrollRunRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails user) {
        if (!currentUser.canManagePayroll(user))
            throw new AccessDeniedException("Payroll access is restricted to administrators and finance.");
        for (var input : request.employees()) {
            if (!employees.existsById(input.employeeId())) throw new ResourceNotFoundException("Employee", input.employeeId());
        }
        List<SuiteRecord> records = payrollRunService.calculateAndGenerate(request, user.getUser().getId());
        return ApiResponse.ok("Payroll calculated. Finance approval is required before payslips can be emailed.", records);
    }

    @PostMapping("/payroll/{id}/approve-and-email")
    public ApiResponse<SuiteRecord> approveAndEmailPayslip(@PathVariable UUID id,
                                                             @AuthenticationPrincipal CustomUserDetails user) {
        requirePayrollAccess(user);
        SuiteRecord payroll = repository.findByIdAndModule(id, "payroll")
                .orElseThrow(() -> new ResourceNotFoundException("Payroll record", id));
        if (payroll.getArchivedAt() != null) throw new BadRequestException("Restore this archived payroll before approving or emailing it.");
        if (!Set.of("CALCULATED", "EMAIL_FAILED", "APPROVED").contains(payroll.getStatus()))
            throw new BadRequestException("Only calculated or previously approved payroll can be emailed. Current status: " + payroll.getStatus());
        boolean resend = "APPROVED".equals(payroll.getStatus());
        if (repository.claimPayrollEmail(id) != 1)
            throw new BadRequestException("This payslip is already being sent or cannot be sent in its current status.");
        payroll.setStatus("EMAIL_SENDING");
        SuiteRecord payslip = null;
        try {
            payslip = repository.findByModuleAndEmployeeIdAndPeriod("payslips", payroll.getEmployeeId(), payroll.getPeriod())
                    .orElseThrow(() -> new ResourceNotFoundException("Payslip record", payroll.getPeriod()));
            repository.save(payroll);
            payslip.setStatus("EMAIL_SENDING"); repository.save(payslip);
            PayslipDocument document = payslipPdfService.generate(payroll);
            String displayPeriod = YearMonth.parse(payroll.getPeriod())
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
            String body = "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#1f2937;max-width:680px;line-height:1.55\">"
                    + "<p>Dear " + escapeHtml(document.employeeName()) + ",</p>"
                    + "<p>Greetings from TechNext Technologies and Services Private Limited.</p>"
                    + "<p>Please find attached your Payslip for the Month of " + escapeHtml(displayPeriod)
                    + " for your reference and records.</p>"
                    + "<p>Kindly review the details mentioned in the payslip. Should you have any questions or notice any discrepancies regarding your salary, deductions, or payroll-related information, please contact the Accounts Payable Team.</p>"
                    + "<p>We sincerely appreciate your dedication, hard work, and valuable contributions to the organization. Thank you for being an integral part of our continued success.</p>"
                    + "<p>Best Regards,<br>Accounts Payable Team<br>"
                    + "<img src=\"cid:technext-logo\" alt=\"TechNext logo\" style=\"display:block;max-width:180px;max-height:60px;margin:8px 0\">"
                    + "TechNext Technologies And Services Pvt Ltd<br>Email: <a href=\"mailto:ap@technnext.com\">ap@technnext.com</a></p></div>";
            emailService.sendPayslipEmail(document.recipientEmail(), "Payslip for " + displayPeriod, body,
                    document.bytes(), document.filename());
            payroll.setStatus("APPROVED"); payslip.setStatus("EMAILED");
            repository.save(payroll); repository.save(payslip);
            return ApiResponse.ok(resend
                    ? "Payslip email sent again to the employee's recorded email."
                    : "Payroll approved and payslip emailed to the employee's recorded email.", payroll);
        } catch (Exception ex) {
            payroll.setStatus("EMAIL_FAILED");
            repository.save(payroll);
            if (payslip != null) {
                payslip.setStatus("EMAIL_FAILED");
                repository.save(payslip);
            }
            throw new BadRequestException("Payslip email failed. Check the employee email and mail service, then retry.");
        }
    }

    @GetMapping("/payroll/{id}/payslip.pdf")
    public ResponseEntity<byte[]> downloadPayslip(@PathVariable UUID id,
                                                   @AuthenticationPrincipal CustomUserDetails user) {
        requirePayrollAccess(user);
        SuiteRecord payroll = repository.findByIdAndModule(id, "payroll")
                .orElseThrow(() -> new ResourceNotFoundException("Payroll record", id));
        if (payroll.getArchivedAt() != null) throw new BadRequestException("Restore this archived payroll before downloading its payslip.");
        PayslipDocument document = payslipPdfService.generate(payroll);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.filename() + "\"")
                .body(document.bytes());
    }

    private void requirePayrollAccess(CustomUserDetails user) {
        if (!currentUser.canManagePayroll(user))
            throw new AccessDeniedException("Payroll and payslip access is restricted to administrators and finance.");
    }

    @PostMapping("/payroll/{id}/archive")
    public ApiResponse<SuiteRecord> archivePayroll(@PathVariable UUID id,
                                                    @AuthenticationPrincipal CustomUserDetails user) {
        requirePayrollAccess(user);
        return ApiResponse.ok("Payroll and its payslip were archived. Both remain in the audit history and can be restored.",
                payrollArchiveService.archive(id, user.getUser().getId()));
    }

    @PostMapping("/payroll/{id}/restore")
    public ApiResponse<SuiteRecord> restorePayroll(@PathVariable UUID id,
                                                    @AuthenticationPrincipal CustomUserDetails user) {
        requirePayrollAccess(user);
        return ApiResponse.ok("Payroll and its payslip were restored.", payrollArchiveService.restore(id, user.getUser().getId()));
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    @PostMapping("/timesheets/generate")
    public ApiResponse<SuiteRecord> generateTimesheet(
            @Valid @RequestBody TimesheetGenerateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        UUID employeeId = currentUser.resolveActingEmployeeId(user, request.employeeId());
        if (!employees.existsById(employeeId)) throw new ResourceNotFoundException("Employee", employeeId);
        SuiteRecord timesheet = timesheetService.generateFromCompletedTimers(
                employeeId, request.period(), user.getUser().getId());
        return ApiResponse.ok("Timesheet generated from completed time-tracker sessions", timesheet);
    }

    @GetMapping("/payroll/profiles")
    public ApiResponse<List<PayrollProfile>> payrollProfiles(@AuthenticationPrincipal CustomUserDetails user) {
        requireHr(user);
        return ApiResponse.ok(payrollProfiles.findAll());
    }

    @PostMapping("/payroll/profiles")
    public ApiResponse<PayrollProfile> savePayrollProfile(@Valid @RequestBody PayrollProfileRequest request,
                                                           @AuthenticationPrincipal CustomUserDetails user) {
        requireHr(user);
        if (!employees.existsById(request.employeeId())) throw new ResourceNotFoundException("Employee", request.employeeId());
        if (request.basic().add(request.hra()).add(request.lta()).compareTo(request.gross()) > 0)
            throw new BadRequestException("Basic, HRA and LTA cannot exceed gross monthly earnings.");
        PayrollProfile profile = payrollProfiles.findById(request.employeeId()).orElseGet(PayrollProfile::new);
        profile.setEmployeeId(request.employeeId()); profile.setGross(request.gross()); profile.setBasic(request.basic());
        profile.setHra(request.hra()); profile.setLta(request.lta());
        profile.setPfRate(request.pfRate()); profile.setEsiRate(request.esiRate()); profile.setProfessionalTax(request.professionalTax());
        profile.setTds(request.tds()); profile.setEmployerPfRate(request.employerPfRate());
        profile.setEmployerEsiRate(request.employerEsiRate()); profile.setGratuity(request.gratuity());
        profile.setPerformanceBonusEnabled(Boolean.TRUE.equals(request.performanceBonusEnabled()));
        profile.setPerformanceBonus(request.performanceBonus() == null ? java.math.BigDecimal.ZERO : request.performanceBonus());
        profile.setPolicyVersion(request.policyVersion()); profile.setUpdatedBy(user.getUser().getId());
        return ApiResponse.ok("Payroll profile saved", payrollProfiles.save(profile));
    }

    private void requireHr(CustomUserDetails user) {
        if (!currentUser.canManagePayroll(user))
            throw new AccessDeniedException("Payroll configuration is restricted to administrators and finance.");
    }

    @PostMapping("/time-tracking/start")
    public ApiResponse<SuiteRecord> startTimer(@RequestBody java.util.Map<String, String> body,
                                                @AuthenticationPrincipal CustomUserDetails user) {
        String title = body.getOrDefault("title", "").trim();
        if (title.isBlank() || title.length() > 180) throw new BadRequestException("Enter an activity name (maximum 180 characters).");
        UUID employeeId = currentUser.resolveActingEmployeeId(user, null);
        if (repository.existsByModuleAndEmployeeIdAndStatus("time-tracking", employeeId, "RUNNING"))
            throw new BadRequestException("Stop your current timer before starting another.");
        SuiteRecord record = SuiteRecord.builder().module("time-tracking").title(title)
                .employeeId(employeeId).status("RUNNING").startDate(LocalDate.now())
                .createdBy(user.getUser().getId()).build();
        return ApiResponse.ok("Timer started", repository.save(record));
    }

    @PostMapping("/time-tracking/{id}/stop")
    public ApiResponse<SuiteRecord> stopTimer(@PathVariable UUID id,
                                               @AuthenticationPrincipal CustomUserDetails user) {
        SuiteRecord record = repository.findByIdAndModule(id, "time-tracking")
                .orElseThrow(() -> new ResourceNotFoundException("Time tracker", id));
        UUID own = currentUser.ownEmployeeIdOrEmpty(user).orElse(null);
        if (own == null || !own.equals(record.getEmployeeId()))
            throw new AccessDeniedException("Only the employee who started this timer can stop it.");
        if (!"RUNNING".equals(record.getStatus())) throw new BadRequestException("This timer is already stopped.");
        LocalDateTime stoppedAt = LocalDateTime.now();
        int minutes = (int) Math.max(0, Duration.between(record.getCreatedAt(), stoppedAt).toMinutes());
        record.setMinutes(minutes);
        record.setEndDate(stoppedAt.toLocalDate());
        record.setStatus("STOPPED");
        return ApiResponse.ok("Timer stopped", repository.save(record));
    }

    @GetMapping("/{module}")
    public ApiResponse<List<SuiteRecord>> list(@PathVariable String module,
                                                @RequestParam(defaultValue = "false") boolean includeArchived,
                                                @AuthenticationPrincipal CustomUserDetails user) {
        checkModule(module);
        if (Set.of("payroll", "payslips", "statutory").contains(module) && !currentUser.canManagePayroll(user))
            throw new AccessDeniedException("Payroll and payslip records are restricted to administrators and finance.");
        if (isPrivilegedFinance(module, user)) {
            List<SuiteRecord> records = includeArchived
                    ? repository.findByModuleOrderByCreatedAtDesc(module)
                    : repository.findByModuleAndArchivedAtIsNullOrderByCreatedAtDesc(module);
            return ApiResponse.ok(withEmployeeLabels(records));
        }
        if (module.equals("time-tracking")) {
            UUID own = currentUser.ownEmployeeIdOrEmpty(user).orElseThrow(() -> new BadRequestException("No employee profile is linked to your account."));
            return ApiResponse.ok(withEmployeeLabels(repository.findByModuleAndEmployeeIdOrderByCreatedAtDesc(module, own)));
        }
        if (currentUser.isAdmin(user) || currentUser.isSuperAdmin(user))
            return ApiResponse.ok(withEmployeeLabels(repository.findByModuleOrderByCreatedAtDesc(module)));
        UUID own = currentUser.ownEmployeeIdOrEmpty(user).orElseThrow(() -> new BadRequestException("No employee profile is linked to your account."));
        if (module.equals("monitoring")) return ApiResponse.ok(withEmployeeLabels(repository.findByModuleAndEmployeeIdOrderByCreatedAtDesc(module, own)));
        if (module.equals("payslips")) throw new AccessDeniedException("Payslips are delivered to employees by email; portal access is disabled.");
        if (currentUser.isManager(user)) {
            List<UUID> ids = currentUser.accessibleEmployeeIds(user).stream().toList();
            return ApiResponse.ok(withEmployeeLabels(repository.findByModuleAndEmployeeIdInOrderByCreatedAtDesc(module, ids)));
        }
        return ApiResponse.ok(withEmployeeLabels(repository.findByModuleAndEmployeeIdOrderByCreatedAtDesc(module, own)));
    }

    private List<SuiteRecord> withEmployeeLabels(List<SuiteRecord> records) {
        Set<UUID> ids = records.stream().map(SuiteRecord::getEmployeeId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, String> labels = new HashMap<>();
        employees.findAllById(ids).forEach(e -> labels.put(e.getId(), e.getEmployeeCode() + " · " + e.getFirstName() + " " + e.getLastName()));
        records.forEach(record -> record.setEmployeeLabel(labels.get(record.getEmployeeId())));
        return records;
    }

    @PostMapping("/{module}")
    public ApiResponse<SuiteRecord> create(@PathVariable String module, @Valid @RequestBody SuiteRecordRequest body,
                                            @AuthenticationPrincipal CustomUserDetails user) {
        checkModule(module);
        requireWritePermission(module, user);
        UUID employeeId = targetEmployee(module, body.employeeId(), user);
        if (module.equals("monitoring") && !Boolean.TRUE.equals(body.consent()))
            throw new BadRequestException("Explicit employee consent is required before tracking work sessions.");
        SuiteRecord record = toRecord(module, body, employeeId, user);
        record.setStatus(workflowStatus(module, body.status(), user));
        return ApiResponse.ok("Record created", repository.save(record));
    }

    @PutMapping("/{module}/{id}")
    public ApiResponse<SuiteRecord> update(@PathVariable String module, @PathVariable UUID id,
                                            @Valid @RequestBody SuiteRecordRequest body,
                                            @AuthenticationPrincipal CustomUserDetails user) {
        checkModule(module);
        SuiteRecord record = repository.findByIdAndModule(id, module).orElseThrow(() -> new ResourceNotFoundException("HR suite record", id));
        assertCanAccess(record, user);
        requireWritePermission(module, user);
        UUID employeeId = targetEmployee(module, body.employeeId(), user);
        record.setTitle(body.title().trim());
        record.setEmployeeId(employeeId);
        record.setStatus(workflowStatus(module, body.status(), user));
        record.setPeriod(body.period()); record.setAmount(body.amount()); record.setMinutes(body.minutes());
        record.setStartDate(body.startDate()); record.setEndDate(body.endDate()); record.setDetails(body.details());
        if (module.equals("monitoring") && !Boolean.TRUE.equals(body.consent())) {
            record.setConsent(false);
            record.setStatus("CONSENT_WITHDRAWN");
            record.setMinutes(null);
        } else {
            record.setConsent(Boolean.TRUE.equals(body.consent()));
        }
        return ApiResponse.ok("Record updated", repository.save(record));
    }

    private SuiteRecord toRecord(String module, SuiteRecordRequest body, UUID employeeId, CustomUserDetails user) {
        return SuiteRecord.builder().module(module).title(body.title().trim()).employeeId(employeeId)
                .status(normalizeStatus(body.status())).period(body.period()).amount(body.amount()).minutes(body.minutes())
                .startDate(body.startDate()).endDate(body.endDate()).details(body.details())
                .consent(Boolean.TRUE.equals(body.consent())).createdBy(user.getUser().getId()).build();
    }

    private UUID targetEmployee(String module, UUID requested, CustomUserDetails user) {
        if (module.equals("payroll") || module.equals("payslips") || module.equals("statutory") || module.equals("assets")) {
            if (requested == null) throw new BadRequestException("Select an employee for this record.");
            if (Set.of("payroll", "payslips", "statutory").contains(module) && currentUser.canManagePayroll(user)) return requested;
            if (!currentUser.isAdmin(user) && !currentUser.isSuperAdmin(user)) currentUser.assertCanAccessEmployee(user, requested);
            return requested;
        }
        if (module.equals("performance") && !currentUser.isManager(user) && !currentUser.isSuperAdmin(user))
            throw new AccessDeniedException("Performance records can only be created by a manager or HR.");
        if (module.equals("monitoring")) {
            UUID own = currentUser.ownEmployeeId(user);
            if (requested != null && !requested.equals(own)) throw new AccessDeniedException("Work-session consent must be recorded by the employee for themselves.");
            return own;
        }
        if (requested != null && (currentUser.isAdmin(user) || currentUser.isSuperAdmin(user))) {
            return requested;
        }
        return currentUser.resolveActingEmployeeId(user, requested);
    }

    private void requireWritePermission(String module, CustomUserDetails user) {
        if (module.equals("payroll") || module.equals("payslips"))
            throw new AccessDeniedException("Payroll records and payslips are generated by the payroll run workflow.");
        if (module.equals("time-tracking"))
            throw new AccessDeniedException("Use the timer start and stop actions to record work time.");
        boolean payrollAdminOnly = "statutory".equals(module);
        boolean adminOnly = Set.of("payroll", "payslips", "assets").contains(module);
        if (payrollAdminOnly && !currentUser.canManagePayroll(user))
            throw new AccessDeniedException("This module is managed by payroll administrators and finance.");
        if (adminOnly && !currentUser.isAdmin(user) && !currentUser.isSuperAdmin(user))
            throw new AccessDeniedException("This module is managed by HR.");
        if (module.equals("performance") && !currentUser.isManager(user) && !currentUser.isSuperAdmin(user))
            throw new AccessDeniedException("Performance records are managed by a manager or HR.");
        if (module.equals("monitoring") && !currentUser.ownEmployeeIdOrEmpty(user).isPresent())
            throw new BadRequestException("No employee profile is linked to your account.");
    }

    private void assertCanAccess(SuiteRecord record, CustomUserDetails user) {
        if (record.getModule().equals("monitoring")) {
            UUID own = currentUser.ownEmployeeIdOrEmpty(user).orElse(null);
            if (own == null || !own.equals(record.getEmployeeId()))
                throw new AccessDeniedException("Only the employee can change their own tracking consent or work-session record.");
            return;
        }
        boolean payrollModule = Set.of("payroll", "payslips", "statutory").contains(record.getModule());
        if (payrollModule && !currentUser.canManagePayroll(user))
            throw new AccessDeniedException("This module is managed by payroll administrators and finance.");
        if (record.getEmployeeId() != null && !currentUser.isAdmin(user) && !currentUser.isSuperAdmin(user)
                && !(payrollModule && currentUser.canManagePayroll(user))) currentUser.assertCanAccessEmployee(user, record.getEmployeeId());
        else if (!record.getCreatedBy().equals(user.getUser().getId()) && !currentUser.isAdmin(user) && !currentUser.isSuperAdmin(user))
            throw new AccessDeniedException("You cannot access this record.");
    }

    private boolean isPrivilegedFinance(String module, CustomUserDetails user) {
        if (Set.of("payroll", "payslips", "statutory").contains(module)) return currentUser.canManagePayroll(user);
        return "assets".equals(module) && (currentUser.isAdmin(user) || currentUser.isSuperAdmin(user));
    }

    private String normalizeStatus(String status) {
        String value = status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase();
        if (!value.matches("[A-Z_]{1,24}")) throw new BadRequestException("Status must contain only letters and underscores.");
        return value;
    }

    private String workflowStatus(String module, String status, CustomUserDetails user) {
        String value = normalizeStatus(status);
        if (module.equals("timesheets") && !currentUser.isManager(user) && !currentUser.isSuperAdmin(user)
                && !Set.of("OPEN", "DRAFT", "SUBMITTED", "CANCELLED").contains(value))
            throw new AccessDeniedException("Only a manager or HR can approve a timesheet.");
        return value;
    }

    private void checkModule(String module) {
        if (!MODULES.contains(module)) throw new ResourceNotFoundException("HR suite module", module);
    }
}

