package com.technnext.hrms.attendance.service;

import com.technnext.hrms.attendance.entity.Attendance;
import com.technnext.hrms.attendance.repository.AttendanceRepository;
import com.technnext.hrms.email.EmailService;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.leave.entity.LeaveRequest;
import com.technnext.hrms.leave.repository.LeaveRequestRepository;
import com.technnext.hrms.organization.entity.Holiday;
import com.technnext.hrms.organization.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MissedPunchReminderScheduler {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final EmailService emailService;

    /** Check once, one minute after the 9:45 AM cut-off, on Indian weekdays. */
    @Scheduled(cron = "0 46 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void remindEmployeesWithNoCheckIn() {
        LocalDate today = LocalDate.now(INDIA);
        if (isPublicHoliday(today)) {
            log.info("Skipping missed check-in reminders for public holiday {}", today);
            return;
        }

        Map<UUID, Attendance> attendanceToday = attendanceRepository.findByAttendanceDate(today).stream()
                .collect(Collectors.toMap(Attendance::getEmployeeId, Function.identity(), (first, ignored) -> first));
        Set<UUID> onFullDayLeave = leaveRequestRepository.findByStatusOrderByCreatedAtDesc("APPROVED").stream()
                .filter(leave -> isFullDay(leave)
                        && !today.isBefore(leave.getFromDate())
                        && !today.isAfter(leave.getToDate()))
                .map(LeaveRequest::getEmployeeId)
                .collect(Collectors.toSet());

        int reminders = 0;
        for (Employee employee : employeeRepository.findAll()) {
            if (!isActiveEmployee(employee, today) || onFullDayLeave.contains(employee.getId())) continue;
            Attendance attendance = attendanceToday.get(employee.getId());
            if (attendance != null && attendance.getCheckInTime() != null) continue;
            if (employee.getEmail() == null || employee.getEmail().isBlank()) continue;

            emailService.sendMissedCheckInReminder(fullName(employee), employee.getEmail(), today);
            reminders++;
        }
        log.info("Queued {} missed check-in reminder(s) for {}", reminders, today);
    }

    private boolean isPublicHoliday(LocalDate date) {
        return holidayRepository.findByHolidayDateBetween(date, date).stream()
                .anyMatch(holiday -> "PUBLIC".equalsIgnoreCase(holiday.getType()));
    }

    private boolean isFullDay(LeaveRequest leave) {
        return leave.getDayType() == null || "FULL".equalsIgnoreCase(leave.getDayType());
    }

    private boolean isActiveEmployee(Employee employee, LocalDate today) {
        String status = employee.getStatus() == null ? "" : employee.getStatus().toUpperCase();
        return ("ACTIVE".equals(status) || "CONFIRMED".equals(status))
                && "ACTIVE".equalsIgnoreCase(employee.getOnboardingStatus())
                && (employee.getDateOfJoining() == null || !employee.getDateOfJoining().isAfter(today));
    }

    private String fullName(Employee employee) {
        return String.join(" ", employee.getFirstName() == null ? "" : employee.getFirstName(),
                        employee.getMiddleName() == null ? "" : employee.getMiddleName(),
                        employee.getLastName() == null ? "" : employee.getLastName())
                .replaceAll("\\s+", " ").trim();
    }
}

