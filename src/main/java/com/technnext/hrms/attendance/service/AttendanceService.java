package com.technnext.hrms.attendance.service;
import com.technnext.hrms.attendance.dto.CheckRequest;
import com.technnext.hrms.attendance.dto.BulkAttendanceRequest;
import com.technnext.hrms.attendance.entity.Attendance;
import com.technnext.hrms.attendance.entity.AttendanceLog;
import com.technnext.hrms.attendance.repository.AttendanceLogRepository;
import com.technnext.hrms.attendance.repository.AttendanceRepository;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.leave.entity.LeaveRequest;
import com.technnext.hrms.leave.repository.LeaveRequestRepository;
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
public class AttendanceService {
    private final AttendanceRepository attendanceRepository;
    private final AttendanceLogRepository logRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    /** Minimum hours that must be worked for a day to count as PRESENT (else ABSENT). */
    private static final double MIN_PRESENT_HOURS = 4.0;

    /**
     * BUGFIX: an APPROVED leave for a date must take priority over ABSENT.
     *
     * Root cause: the nightly AttendanceAbsentJob (and other paths that default
     * a missing day to ABSENT) never checked the Leave module at all, so a day
     * covered by an approved leave with no attendance row fell straight to
     * ABSENT. This overlays the correct status at read time, using the Leave
     * Requests table (the real source of truth for approved leave) — it does
     * NOT touch stored attendance data, so every other flow (regularization,
     * payroll, reports, the absent job itself) is unaffected.
     *
     * Only a plain ABSENT row with no actual check-in is overridden: an
     * employee who genuinely checked in/out keeps their real PRESENT/HALF_DAY
     * status (existing precedence preserved, per the "don't guess" rule for
     * approved-leave + attendance-record overlap).
     */
    private void applyApprovedLeaveOverride(List<Attendance> rows, List<LeaveRequest> leaves) {
        if (rows.isEmpty() || leaves.isEmpty()) return;
        for (Attendance a : rows) {
            if (!"ABSENT".equalsIgnoreCase(a.getStatus())) continue;
            if (a.getCheckInTime() != null) continue;
            boolean onApprovedLeave = leaves.stream().anyMatch(l ->
                    "APPROVED".equalsIgnoreCase(l.getStatus())
                    && l.getEmployeeId().equals(a.getEmployeeId())
                    && !a.getAttendanceDate().isBefore(l.getFromDate())
                    && !a.getAttendanceDate().isAfter(l.getToDate()));
            if (onApprovedLeave) {
                a.setStatus("LEAVE");
            }
        }
    }

    public List<Attendance> history(UUID employeeId) {
        List<Attendance> rows = attendanceRepository.findByEmployeeIdOrderByAttendanceDateDesc(employeeId);
        applyApprovedLeaveOverride(rows, leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId));
        return rows;
    }
    public List<Attendance> forDate(LocalDate date) {
        List<Attendance> rows = attendanceRepository.findByAttendanceDate(date);
        applyApprovedLeaveOverride(rows, leaveRequestRepository.findByStatusOrderByCreatedAtDesc("APPROVED"));
        return rows;
    }
    @Transactional
    public int bulkMark(BulkAttendanceRequest req) {
        if (req.employeeIds() == null || req.employeeIds().isEmpty()) return 0;
        LocalDate date = req.date() != null ? req.date() : LocalDate.now();
        String status = (req.status() == null || req.status().isBlank()) ? "PRESENT" : req.status();
        int created = 0;
        for (UUID empId : req.employeeIds()) {
            if (empId == null) continue;
            boolean exists = attendanceRepository.findByEmployeeIdAndAttendanceDate(empId, date).isPresent();
            if (exists) continue;
            Attendance a = new Attendance();
            a.setEmployeeId(empId);
            a.setAttendanceDate(date);
            a.setStatus(status);
            a.setRemarks(req.remarks());
            a.setIsRegularized(true);
            attendanceRepository.save(a);
            created++;
        }
        return created;
    }

    /**
     * Web check-in: creates today's attendance row with check-in time (once per day).
     *
     * Self-service check-in REQUIRES the employee's live location and a selfie.
     */
    @Transactional
    public Attendance checkIn(CheckRequest req) {
        if (req.employeeId() == null) {
            throw new BadRequestException("employeeId is required");
        }
        if (req.latitude() == null || req.longitude() == null) {
            throw new BadRequestException("Location is required to check in. Please allow location access.");
        }
        if (req.checkInPhotoId() == null) {
            throw new BadRequestException("A check-in photo is required. Please allow camera access and capture a photo.");
        }

        LocalDate today = LocalDate.now();
        var existing = attendanceRepository.findByEmployeeIdAndAttendanceDate(req.employeeId(), today);

        if (existing.isPresent()) {
            Attendance att = existing.get();
            if (att.getCheckInTime() != null) {
                throw new BadRequestException("Already checked in today");
            }
            // Row exists (e.g. bulk-marked) but has no check-in time yet
            att.setCheckInTime(LocalDateTime.now());
            att.setStatus("PRESENT");
            att.setCheckInLatitude(req.latitude());
            att.setCheckInLongitude(req.longitude());
            att.setCheckInPhotoId(req.checkInPhotoId());
            Attendance saved = attendanceRepository.save(att);
            saveLog(req, "IN", LocalDateTime.now());
            return saved;
        }

        Attendance att = Attendance.builder()
                .employeeId(req.employeeId())
                .attendanceDate(today)
                .status("PRESENT")
                .isRegularized(false)
                .checkInTime(LocalDateTime.now())
                .checkInLatitude(req.latitude())
                .checkInLongitude(req.longitude())
                .checkInPhotoId(req.checkInPhotoId())
                .build();
        Attendance saved = attendanceRepository.save(att);
        saveLog(req, "IN", LocalDateTime.now());
        return saved;
    }

    /**
     * Web check-out: sets check-out time and computes working hours.
     *
     * #2: a day counts as PRESENT only if at least MIN_PRESENT_HOURS (4h) were
     *     worked; otherwise it is marked ABSENT.
     *
     * Self-service check-out REQUIRES location + a selfie, same as check-in.
     */
    @Transactional
    public Attendance checkOut(CheckRequest req) {
        if (req.employeeId() == null) {
            throw new BadRequestException("employeeId is required");
        }
        if (req.checkOutLatitude() == null || req.checkOutLongitude() == null) {
            throw new BadRequestException("Location is required to check out. Please allow location access.");
        }
        if (req.checkOutPhotoId() == null) {
            throw new BadRequestException("A check-out photo is required. Please allow camera access and capture a photo.");
        }

        LocalDate today = LocalDate.now();
        Attendance att = attendanceRepository
                .findByEmployeeIdAndAttendanceDate(req.employeeId(), today)
                .orElseThrow(() -> new BadRequestException("No check-in found for today"));
        if (att.getCheckInTime() == null) {
            throw new BadRequestException("You must check in before checking out");
        }
        if (att.getCheckOutTime() != null) {
            throw new BadRequestException("Already checked out today");
        }

        LocalDateTime now = LocalDateTime.now();
        att.setCheckOutTime(now);
        att.setCheckOutLatitude(req.checkOutLatitude());
        att.setCheckOutLongitude(req.checkOutLongitude());
        att.setCheckOutPhotoId(req.checkOutPhotoId());

        long minutes = Math.max(0, Duration.between(att.getCheckInTime(), now).toMinutes());
        double hoursWorked = minutes / 60.0;
        att.setWorkingHours(BigDecimal.valueOf(hoursWorked).setScale(2, RoundingMode.HALF_UP));

        // #2: under 4 hours worked -> ABSENT, otherwise PRESENT.
        // Don't override a regularized row's status (HR/manager set that deliberately).
        if (!Boolean.TRUE.equals(att.getIsRegularized())) {
            att.setStatus(hoursWorked < MIN_PRESENT_HOURS ? "ABSENT" : "PRESENT");
        }

        Attendance saved = attendanceRepository.save(att);

        saveLog(req, "OUT", now);
        return saved;
    }

    private void saveLog(CheckRequest req, String logType, LocalDateTime time) {
        logRepository.save(AttendanceLog.builder()
                .employeeId(req.employeeId())
                .logTime(time)
                .logType(logType)
                .ipAddress(req.ipAddress())
                .deviceInfo(req.deviceInfo())
                .build());
    }
}