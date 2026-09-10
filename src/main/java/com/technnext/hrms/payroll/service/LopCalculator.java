package com.technnext.hrms.payroll.service;

import com.technnext.hrms.attendance.entity.Attendance;
import com.technnext.hrms.attendance.repository.AttendanceRepository;
import com.technnext.hrms.leave.entity.LeaveType;
import com.technnext.hrms.leave.repository.LeaveRequestRepository;
import com.technnext.hrms.leave.repository.LeaveTypeRepository;
import com.technnext.hrms.organization.entity.Holiday;
import com.technnext.hrms.organization.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Determines how many of a month's working days an employee actually gets
 * paid for. Reuses the same weekend/public-holiday rules as LeaveDaysCalculator
 * (Sat+Sun off, PUBLIC holidays off) so Payroll and Leave never disagree about
 * what counts as a working day.
 *
 * A day counts as PAID if the employee has an Attendance row marked PRESENT
 * (or is on an APPROVED leave request whose LeaveType is paid). Any other
 * working day — no attendance row, an unapproved/rejected leave, or an
 * unpaid leave type — is Loss of Pay (LOP).
 */
@Service
@RequiredArgsConstructor
public class LopCalculator {

    private final HolidayRepository holidayRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public record LopResult(BigDecimal workingDays, BigDecimal paidDays, BigDecimal lopDays) {}

    public LopResult calculate(UUID employeeId, int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Set<LocalDate> publicHolidays = holidayRepository.findByHolidayDateBetween(from, to).stream()
                .filter(h -> "PUBLIC".equalsIgnoreCase(h.getType()))
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        int workingDayCount = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isWorkingDay(d, publicHolidays)) workingDayCount++;
        }
        BigDecimal workingDays = BigDecimal.valueOf(workingDayCount);
        if (workingDayCount == 0) {
            return new LopResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        Map<LocalDate, Attendance> attendanceByDate = attendanceRepository
                .findByEmployeeIdAndAttendanceDateBetween(employeeId, from, to).stream()
                .collect(Collectors.toMap(Attendance::getAttendanceDate, a -> a, (a, b) -> a));

        Map<Integer, LeaveType> leaveTypesById = leaveTypeRepository.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, lt -> lt));
        var approvedLeaves = leaveRequestRepository.findApprovedOverlapping(employeeId, from, to);

        BigDecimal paidDays = BigDecimal.ZERO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (!isWorkingDay(d, publicHolidays)) continue;

            Attendance att = attendanceByDate.get(d);
            if (att != null && "PRESENT".equalsIgnoreCase(att.getStatus())) {
                paidDays = paidDays.add(BigDecimal.ONE);
                continue;
            }

            LocalDate finalD = d;
            boolean onPaidLeave = approvedLeaves.stream().anyMatch(lr -> {
                if (finalD.isBefore(lr.getFromDate()) || finalD.isAfter(lr.getToDate())) return false;
                LeaveType lt = leaveTypesById.get(lr.getLeaveTypeId());
                return lt != null && Boolean.TRUE.equals(lt.getIsPaid());
            });
            if (onPaidLeave) {
                boolean halfDay = approvedLeaves.stream().anyMatch(lr ->
                        finalD.equals(lr.getFromDate()) && finalD.equals(lr.getToDate()) && "HALF_DAY".equalsIgnoreCase(lr.getDayType()));
                paidDays = paidDays.add(halfDay ? new BigDecimal("0.5") : BigDecimal.ONE);
            }
        }

        BigDecimal lopDays = workingDays.subtract(paidDays).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new LopResult(workingDays, paidDays.setScale(2, RoundingMode.HALF_UP), lopDays);
    }

    private boolean isWorkingDay(LocalDate d, Set<LocalDate> publicHolidays) {
        DayOfWeek dow = d.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return false;
        return !publicHolidays.contains(d);
    }
}