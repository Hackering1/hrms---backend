package com.technnext.hrms.attendance.service;

import com.technnext.hrms.attendance.entity.Attendance;
import com.technnext.hrms.attendance.repository.AttendanceRepository;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.leave.entity.LeaveRequest;
import com.technnext.hrms.leave.repository.LeaveRequestRepository;
import com.technnext.hrms.organization.entity.Holiday;
import com.technnext.hrms.organization.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * #2: Nightly job that marks employees ABSENT when they miss their attendance.
 *
 * The web check-out flow already marks a day ABSENT when fewer than the minimum
 * hours were worked, but it can only run if the employee actually checked out.
 * Two cases slip through and are handled here:
 *   1. The employee never checked in at all  -> no attendance row exists.
 *   2. The employee checked in but never checked out -> the row is stuck at the
 *      default PRESENT with no check-out time.
 *
 * The job runs shortly after midnight and processes the PREVIOUS day so the full
 * working day (including any late check-out) has already elapsed.
 *
 * Weekends (Saturday/Sunday) and public/restricted holidays are skipped so
 * nobody is marked absent on a non-working day (matches the calendar, #3).
 */
@Component
@RequiredArgsConstructor
public class AttendanceAbsentJob {

    private static final Logger log = LoggerFactory.getLogger(AttendanceAbsentJob.class);

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    /**
     * Runs every day at 00:30 server time and marks the previous day.
     * cron = second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "0 30 0 * * *")
    @Transactional
    public void markMissedAsAbsent() {
        LocalDate day = LocalDate.now().minusDays(1);
        int marked = markAbsentForDate(day);
        log.info("Absent job for {} completed: {} row(s) marked ABSENT", day, marked);
    }

    /**
     * Marks ABSENT for a specific date. Returns the number of rows created or
     * updated. Exposed (package-visible) so it can be triggered manually if HR
     * needs to backfill a specific date.
     */
    @Transactional
    public int markAbsentForDate(LocalDate day) {
        // Skip weekends — Saturday and Sunday are non-working by default.
        DayOfWeek dow = day.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return 0;
        }
        // Skip public / restricted holidays.
        List<Holiday> holidays = holidayRepository.findByHolidayDateBetween(day, day);
        if (!holidays.isEmpty()) {
            return 0;
        }

        int affected = 0;
        List<Employee> employees = employeeRepository.findAll();
        for (Employee emp : employees) {
            // Only consider currently active employees.
            if (emp.getStatus() != null && emp.getStatus().equalsIgnoreCase("EXITED")) {
                continue;
            }
            // Never mark a date before the employee even joined.
            if (emp.getDateOfJoining() != null && day.isBefore(emp.getDateOfJoining())) {
                continue;
            }
            // BUGFIX: skip days covered by an APPROVED leave — an approved leave
            // must never be turned into ABSENT (matches AttendanceService's
            // read-time correction, and keeps the stored row from ever going
            // wrong in the first place for new dates).
            if (hasApprovedLeave(emp.getId(), day)) {
                continue;
            }

            Optional<Attendance> existing =
                    attendanceRepository.findByEmployeeIdAndAttendanceDate(emp.getId(), day);

            if (existing.isEmpty()) {
                // Case 1: no row at all -> employee never checked in. Mark ABSENT.
                Attendance a = new Attendance();
                a.setEmployeeId(emp.getId());
                a.setAttendanceDate(day);
                a.setStatus("ABSENT");
                a.setRemarks("Auto-marked absent: no check-in recorded");
                attendanceRepository.save(a);
                affected++;
            } else {
                Attendance a = existing.get();
                // Never override a manually regularized row.
                if (Boolean.TRUE.equals(a.getIsRegularized())) {
                    continue;
                }
                // Case 2: checked in but never checked out -> incomplete day.
                boolean missedCheckOut = a.getCheckOutTime() == null;
                boolean missedCheckIn = a.getCheckInTime() == null;
                if (missedCheckOut || missedCheckIn) {
                    if (!"ABSENT".equalsIgnoreCase(a.getStatus())) {
                        a.setStatus("ABSENT");
                        String reason = missedCheckIn
                                ? "Auto-marked absent: no check-in recorded"
                                : "Auto-marked absent: no check-out recorded";
                        a.setRemarks(reason);
                        attendanceRepository.save(a);
                        affected++;
                    }
                }
            }
        }
        return affected;
    }

    private boolean hasApprovedLeave(java.util.UUID employeeId, LocalDate day) {
        List<LeaveRequest> leaves = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
        for (LeaveRequest l : leaves) {
            if ("APPROVED".equalsIgnoreCase(l.getStatus())
                    && !day.isBefore(l.getFromDate())
                    && !day.isAfter(l.getToDate())) {
                return true;
            }
        }
        return false;
    }
}