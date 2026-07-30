package com.technnext.hrms.invite.service;

import com.technnext.hrms.auth.service.AuthService;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.InvalidInviteException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.document.dto.EmployeeDocumentCreate;
import com.technnext.hrms.document.repository.DocumentCategoryRepository;
import com.technnext.hrms.document.service.EmployeeDocumentService;
import com.technnext.hrms.email.EmployeeInviteEmailEvent;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.entity.EmployeeManager;
import com.technnext.hrms.employee.repository.EmployeeManagerRepository;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.employee.service.EmployeeService;
import com.technnext.hrms.invite.dto.InviteEmployeeRequest;
import com.technnext.hrms.invite.dto.OnboardingCompleteRequest;
import com.technnext.hrms.invite.dto.OnboardingDocumentDto;
import com.technnext.hrms.invite.dto.OnboardingInfoResponse;
import com.technnext.hrms.invite.entity.EmployeeInvite;
import com.technnext.hrms.invite.repository.EmployeeInviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeInviteService {

    private final EmployeeInviteRepository inviteRepository;
    private final EmployeeService employeeService;
    private final AuthService authService;
    private final EmployeeDocumentService employeeDocumentService;
    private final DocumentCategoryRepository documentCategoryRepository;
    private final com.technnext.hrms.file.service.FileStorageService fileStorageService;
    private final EmployeeManagerRepository employeeManagerRepository;
    private final EmployeeRepository employeeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.onboarding.link-base-url}")
    private String onboardingLinkBaseUrl;

    private static final long TOKEN_VALID_HOURS = 24;

    // =========================================================================
    // ADMIN SIDE (Super Admin only — enforced by @PreAuthorize on the controller)
    // =========================================================================

    @Transactional
    public EmployeeInvite sendInvitation(InviteEmployeeRequest req) {
        if (req.email() == null || req.email().isBlank()) {
            throw new BadRequestException("Login Email is required");
        }
        if (req.loginRole() == null || req.loginRole().isBlank()) {
            throw new BadRequestException("Create Login As is required");
        }
        if (authService.emailAlreadyRegistered(req.email())) {
            throw new BadRequestException("Email already registered: " + req.email());
        }

        Employee shell = employeeService.createInviteShell(req);

        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        EmployeeInvite invite = EmployeeInvite.builder()
                .employeeId(shell.getId())
                .token(token)
                .email(req.email().trim())
                .loginRole(req.loginRole())
                .status("PENDING")
                .expiresAt(LocalDateTime.now().plusHours(TOKEN_VALID_HOURS))
                .build();
        inviteRepository.save(invite);

        String link = onboardingLinkBaseUrl + "?token=" + token;
        eventPublisher.publishEvent(new EmployeeInviteEmailEvent(
                invite.getEmail(), shell.getFirstName(), shell.getLastName(),
                shell.getEmployeeCode(), link));

        return invite;
    }

    @Transactional(readOnly = true)
    public List<EmployeeInvite> listInvites() {
        return inviteRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<com.technnext.hrms.employee.dto.EmployeeResponse> listPendingProfiles() {
        List<UUID> ids = employeeRepository.findByOnboardingStatus("INVITED").stream()
                .map(Employee::getId)
                .toList();
        return employeeService.getByIds(ids);
    }

    @Transactional
    public EmployeeInvite resendInvitation(UUID inviteId) {
        EmployeeInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", inviteId));
        if ("USED".equals(invite.getStatus())) {
            throw new BadRequestException("This invitation has already been used and cannot be resent.");
        }
        invite.setToken(UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", ""));
        invite.setStatus("PENDING");
        invite.setExpiresAt(LocalDateTime.now().plusHours(TOKEN_VALID_HOURS));
        inviteRepository.save(invite);

        Employee shell = employeeService.getEntityById(invite.getEmployeeId());
        String link = onboardingLinkBaseUrl + "?token=" + invite.getToken();
        eventPublisher.publishEvent(new EmployeeInviteEmailEvent(
                invite.getEmail(), shell.getFirstName(), shell.getLastName(),
                shell.getEmployeeCode(), link));
        return invite;
    }

    @Transactional
    public void cancelInvitation(UUID inviteId) {
        EmployeeInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", inviteId));
        if ("USED".equals(invite.getStatus())) {
            throw new BadRequestException("This invitation has already been used and cannot be cancelled.");
        }
        invite.setStatus("CANCELLED");
        inviteRepository.save(invite);
    }

    // =========================================================================
    // PUBLIC / CANDIDATE SIDE (no authentication — token IS the credential)
    // =========================================================================

    /** Loads + validates the invite, or throws a typed InvalidInviteException. */
    private EmployeeInvite validateToken(String token) {
        EmployeeInvite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new InvalidInviteException(
                        InvalidInviteException.Reason.NOT_FOUND, "Invalid invitation link."));
        if ("USED".equals(invite.getStatus())) {
            throw new InvalidInviteException(
                    InvalidInviteException.Reason.ALREADY_USED, "This invitation has already been used.");
        }
        if ("CANCELLED".equals(invite.getStatus())) {
            throw new InvalidInviteException(
                    InvalidInviteException.Reason.CANCELLED, "This invitation has been cancelled.");
        }
        if (invite.isExpired()) {
            throw new InvalidInviteException(
                    InvalidInviteException.Reason.EXPIRED, "This invitation has expired.");
        }
        return invite;
    }

    @Transactional(readOnly = true)
    public OnboardingInfoResponse getOnboardingInfo(String token) {
        EmployeeInvite invite = validateToken(token);
        Employee e = employeeService.getEntityById(invite.getEmployeeId());

        String managerName = null;
        List<EmployeeManager> links = employeeManagerRepository.findByEmployeeId(e.getId());
        if (!links.isEmpty()) {
            UUID managerId = links.get(0).getManagerId();
            managerName = employeeRepository.findById(managerId)
                    .map(m -> (safe(m.getFirstName()) + " " + safe(m.getLastName())).trim())
                    .orElse(null);
        }

        long minutesRemaining = Duration.between(LocalDateTime.now(), invite.getExpiresAt()).toMinutes();

        return new OnboardingInfoResponse(
                e.getEmployeeCode(),
                e.getFirstName(),
                e.getLastName(),
                invite.getEmail(),
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getDesignation() != null ? e.getDesignation().getName() : null,
                managerName,
                e.getDateOfJoining() != null ? e.getDateOfJoining().toString() : null,
                invite.getLoginRole(),
                Math.max(0, minutesRemaining)
        );
    }

    /** Token-scoped file upload — candidate has no JWT yet, so this mirrors
     *  FileController's authenticated upload but requires a valid token instead. */
    @Transactional
    public Map<String, Object> uploadDocument(String token, MultipartFile file) {
        validateToken(token); // just to gate the upload; not otherwise used here
        var stored = fileStorageService.store(file, null);
        Map<String, Object> out = new HashMap<>();
        out.put("id", stored.getId());
        out.put("fileName", stored.getFileName() != null ? stored.getFileName() : "");
        out.put("contentType", stored.getContentType() != null ? stored.getContentType() : "");
        out.put("size", stored.getFileSize() != null ? stored.getFileSize() : 0L);
        out.put("url", "/api/files/" + stored.getId());
        return out;
    }

    @Transactional
    public void completeOnboarding(String token, OnboardingCompleteRequest req) {
        EmployeeInvite invite = validateToken(token);

        if (req.password() == null || req.password().length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }
        if (!req.password().equals(req.confirmPassword())) {
            throw new BadRequestException("Password and Confirm Password do not match");
        }

        UUID employeeId = invite.getEmployeeId();

        Employee saved = employeeService.applyOnboardingData(employeeId, req);
        saveDocuments(employeeId, req.documents());

        UUID newUserId = authService.createUserAccountWithOwnPassword(
                invite.getEmail(), invite.getLoginRole(), req.password());

        employeeService.markOnboardingComplete(employeeId, newUserId);

        invite.setStatus("USED");
        invite.setUsedAt(LocalDateTime.now());
        inviteRepository.save(invite);
    }

    // ---------- helpers ----------

    private void saveDocuments(UUID employeeId, List<OnboardingDocumentDto> documents) {
        if (documents == null) return;
        for (OnboardingDocumentDto doc : documents) {
            if (doc == null || doc.fileUrl() == null || doc.fileUrl().isBlank()) continue;
            String docType = doc.docType() == null ? "OTHER" : doc.docType().toUpperCase();

            if ("PROFILE_PHOTO".equals(docType)) {
                employeeService.setProfilePhotoUrl(employeeId, doc.fileUrl());
                continue;
            }

            String categoryName;
            String label;
            switch (docType) {
                case "DEGREE_CERTIFICATE" -> { categoryName = "Educational Documents"; label = "Degree Certificate"; }
                case "EXPERIENCE_CERTIFICATE" -> { categoryName = "Experience Documents"; label = "Experience Certificate"; }
                case "RESUME" -> { categoryName = "Personal Documents"; label = "Resume"; }
                case "AADHAAR" -> { categoryName = "Personal Documents"; label = "Aadhaar Card"; }
                case "PAN" -> { categoryName = "Personal Documents"; label = "PAN Card"; }
                case "BANK_PASSBOOK" -> { categoryName = "Personal Documents"; label = "Bank Passbook"; }
                default -> { categoryName = "Personal Documents"; label = "Other Document"; }
            }

            Integer categoryId = documentCategoryRepository.findByNameIgnoreCase(categoryName)
                    .map(c -> c.getId())
                    .orElse(null);
            if (categoryId == null) continue; // category seeder guarantees these exist; defensive no-op if not

            employeeDocumentService.create(new EmployeeDocumentCreate(
                    employeeId, categoryId, label, doc.fileUrl(), null, null, null, null));
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}