package com.technnext.hrms.attendance.service;

import com.technnext.hrms.attendance.dto.RegularizationCreate;
import com.technnext.hrms.attendance.dto.RegularizationDecision;
import com.technnext.hrms.attendance.entity.Attendance;
import com.technnext.hrms.attendance.entity.AttendanceRegularization;
import com.technnext.hrms.attendance.repository.AttendanceRegularizationRepository;
import com.technnext.hrms.attendance.repository.AttendanceRepository;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.notification.dto.NotificationCreate;
import com.technnext.hrms.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegularizationService {

    private final AttendanceRegularizationRepository regRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    // ─── Queries ───────────────────────────────────────────────────────────────

    public List<AttendanceRegularization> getAll() {
        return regRepository.findAll();
    }

    public List<AttendanceRegularization> getByEmployee(UUID employeeId) {
        return regRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    public List<AttendanceRegularization> getPending() {
        return regRepository.findByStatusOrderByCreatedAtDesc("PENDING");
    }

    /**
     * NEW: fetch a single regularization by id. Used by the controller to check
     * whose request it is before letting a manager approve/reject (team-routed
     * approvals). Throws if not found.
     */
    @Transactional(readOnly = true)
    public AttendanceRegularization getById(Integer id) {
        return regRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Regularization", id));
    }

    // ─── Create ────────────────────────────────────────────────────────────────

    /**
     * Employee raises a regularization request.
     *
     * FIX 1: Block future dates — can't regularize what hasn't happened yet.
     * FIX 2: Block duplicate active requests for the same date — prevents spamming
     *         the manager with multiple requests for the same day.
     * FIX 3: Validate reason is not blank.
     * FIX 4: Notify all managers + HR of the new pending request so they don't
     *         have to poll the portal to discover it.
     */
    @Transactional
    public AttendanceRegularization create(RegularizationCreate req) {
        // FIX 1: future date guard
        if (req.attendanceDate() == null) {
            throw new BadRequestException("attendanceDate is required");
        }
        if (req.attendanceDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Cannot raise a regularization request for a future date");
        }

        // FIX 2: duplicate guard — one active (PENDING or APPROVED) request per date
        boolean alreadyActive = regRepository
                .findByEmployeeIdOrderByCreatedAtDesc(req.employeeId())
                .stream()
                .anyMatch(r -> r.getAttendanceDate().equals(req.attendanceDate())
                        && ("PENDING".equals(r.getStatus()) || "APPROVED".equals(r.getStatus())));
        if (alreadyActive) {
            throw new BadRequestException(
                    "A regularization request already exists for " + req.attendanceDate()
                    + ". Cancel the existing request before raising a new one.");
        }

        // FIX 3: reason required
        if (req.reason() == null || req.reason().isBlank()) {
            throw new BadRequestException("Reason is required");
        }

        AttendanceRegularization reg = AttendanceRegularization.builder()
                .employeeId(req.employeeId())
                .attendanceDate(req.attendanceDate())
                .requestedIn(req.requestedIn())
                .requestedOut(req.requestedOut())
                .reason(req.reason())
                .status("PENDING")
                .build();
        reg = regRepository.save(reg);

        // FIX 4: notify the employee's direct manager (if any) via the notification system
        notifyManagerOfNewRequest(reg);

        return reg;
    }

    // ─── Decide ────────────────────────────────────────────────────────────────

    /**
     * Manager / HR / Super Admin approves or rejects a regularization request.
     *
     * FIX 1: On APPROVE — actually apply the corrected check-in/out times to the
     *         attendance row (the original code only set isRegularized=true and
     *         never updated the times or recalculated working hours).
     * FIX 2: On APPROVE — create the attendance row from scratch if none exists
     *         (employee forgot to check in at all, so no row was ever created).
     * FIX 3: On APPROVE — recalculate working hours from the corrected times.
     * FIX 4: On APPROVE or REJECT — send the employee an in-app notification
     *         with the decision and the reviewer's remarks.
     * FIX 5: reviewedBy is now stored correctly (was always null before because
     *         the frontend wasn't sending it — handled on the frontend side).
     */
    @Transactional
    public AttendanceRegularization decide(Integer id, RegularizationDecision decision) {
        AttendanceRegularization reg = regRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Regularization", id));

        if (!"PENDING".equals(reg.getStatus())) {
            throw new BadRequestException("Only PENDING regularizations can be decided");
        }
        String status = decision.status();
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new BadRequestException("status must be APPROVED or REJECTED");
        }

        reg.setStatus(status);
        reg.setReviewedBy(decision.reviewedBy());
        reg.setReviewedAt(LocalDateTime.now());
        reg.setReviewerRemarks(decision.reviewerRemarks());
        regRepository.save(reg);

        if ("APPROVED".equals(status)) {
            applyApprovedCorrection(reg);
        }

        // FIX 4: notify the employee
        notifyEmployeeOfDecision(reg);

        return reg;
    }

    // ─── Cancel ────────────────────────────────────────────────────────────────

    /**
     * Employee cancels their own PENDING request (e.g. they managed to get the
     * HR to fix it directly, or raised the wrong date).
     */
    @Transactional
    public void cancel(Integer id, UUID requestingEmployeeId) {
        AttendanceRegularization reg = regRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Regularization", id));
        if (!reg.getEmployeeId().equals(requestingEmployeeId)) {
            throw new BadRequestException("You can only cancel your own requests");
        }
        if (!"PENDING".equals(reg.getStatus())) {
            throw new BadRequestException("Only PENDING requests can be cancelled");
        }
        reg.setStatus("CANCELLED");
        regRepository.save(reg);
    }

    /**
     * HR/Admin permanently removes a regularization request row (typically
     * old test data, or a decided request that's cluttering the list). Unlike
     * {@link #cancel}, this actually deletes the row rather than just marking
     * it CANCELLED — the "Cancel"/"Actions" column had no way to ever remove
     * an already-decided (APPROVED/REJECTED/CANCELLED) request, so they piled
     * up with no cleanup path.
     */
    @Transactional
    public void deletePermanent(Integer id) {
        AttendanceRegularization reg = regRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Regularization", id));
        regRepository.delete(reg);
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /**
     * FIX 1+2+3: Apply the corrected times to the attendance row, or create
     * the row if it doesn't exist yet.
     */
    private void applyApprovedCorrection(AttendanceRegularization reg) {
        Attendance att = attendanceRepository
                .findByEmployeeIdAndAttendanceDate(reg.getEmployeeId(), reg.getAttendanceDate())
                .orElse(null);

        if (att == null) {
            // FIX 2: No attendance row at all — create one from scratch
            att = Attendance.builder()
                    .employeeId(reg.getEmployeeId())
                    .attendanceDate(reg.getAttendanceDate())
                    .status("PRESENT")
                    .isRegularized(true)
                    .remarks("Regularized: " + reg.getReason())
                    .build();
        } else {
            att.setIsRegularized(true);
            att.setRemarks("Regularized: " + reg.getReason());
        }

        // FIX 1: Apply the corrected check-in/check-out times the employee requested
        if (reg.getRequestedIn() != null) {
            att.setCheckInTime(reg.getAttendanceDate().atTime(reg.getRequestedIn()));
        }
        if (reg.getRequestedOut() != null) {
            att.setCheckOutTime(reg.getAttendanceDate().atTime(reg.getRequestedOut()));
        }

        // FIX 3: Recalculate working hours from corrected times
        if (att.getCheckInTime() != null && att.getCheckOutTime() != null) {
            long minutes = Math.max(0,
                    Duration.between(att.getCheckInTime(), att.getCheckOutTime()).toMinutes());
            double hoursWorked = minutes / 60.0;
            att.setWorkingHours(
                    BigDecimal.valueOf(hoursWorked).setScale(2, RoundingMode.HALF_UP));
            // Recompute status from the corrected hours: >= 4h worked -> PRESENT,
            // otherwise ABSENT. Without this an already-ABSENT row stayed ABSENT
            // even after a valid full-day regularization was approved.
            att.setStatus(hoursWorked < 4.0 ? "ABSENT" : "PRESENT");
        }

        attendanceRepository.save(att);
    }

    /**
     * FIX 4a: Notify managers/HR when a new request is raised.
     */
    private void notifyManagerOfNewRequest(AttendanceRegularization reg) {
        try {
            String empName = employeeName(reg.getEmployeeId());
            notificationService.createBroadcast(
                    "New Regularization Request",
                    empName + " has raised an attendance regularization request for "
                            + reg.getAttendanceDate() + ". Reason: " + reg.getReason(),
                    "INFO",
                    "ATTENDANCE",
                    String.valueOf(reg.getId())
            );
        } catch (Exception ignored) {
            // Non-fatal — don't roll back the regularization just because notification failed
        }
    }

    /**
     * FIX 4b: Notify the employee of the approval/rejection decision.
     */
    private void notifyEmployeeOfDecision(AttendanceRegularization reg) {
        try {
            Employee emp = employeeRepository.findById(reg.getEmployeeId()).orElse(null);
            if (emp == null || emp.getUserId() == null) return;

            boolean approved = "APPROVED".equals(reg.getStatus());
            String title = approved
                    ? "Attendance Regularization Approved"
                    : "Attendance Regularization Rejected";
            String message = approved
                    ? "Your attendance regularization for " + reg.getAttendanceDate()
                      + " has been approved. Your attendance record has been corrected."
                    : "Your attendance regularization for " + reg.getAttendanceDate()
                      + " has been rejected."
                      + (reg.getReviewerRemarks() != null && !reg.getReviewerRemarks().isBlank()
                              ? " Reason: " + reg.getReviewerRemarks() : "");

            notificationService.create(new NotificationCreate(
                    emp.getUserId(),
                    title,
                    message,
                    approved ? "SUCCESS" : "WARNING",
                    "ATTENDANCE",
                    String.valueOf(reg.getId())
            ));
        } catch (Exception ignored) {
            // Non-fatal
        }
    }

    private String employeeName(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .map(e -> (e.getFirstName() != null ? e.getFirstName() : "")
                        + " " + (e.getLastName() != null ? e.getLastName() : ""))
                .orElse("Employee");
    }
}
