package com.technnext.hrms.report.service;

import com.technnext.hrms.attendance.entity.Attendance;
import com.technnext.hrms.attendance.repository.AttendanceRepository;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.leave.entity.LeaveRequest;
import com.technnext.hrms.leave.repository.LeaveRequestRepository;
import com.technnext.hrms.report.dto.AttendanceReport;
import com.technnext.hrms.report.dto.EmployeeReport;
import com.technnext.hrms.report.dto.LeaveReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    @Transactional(readOnly = true)
    public EmployeeReport employeeReport() {
        List<Employee> all = employeeRepository.findAll();
        // "Total Employees" = active headcount, not everyone ever created. A
        // soft-deleted employee still exists in the table (to preserve their
        // attendance/leave history) but shouldn't inflate this number — it was
        // showing 5 even when every one of those 5 had been removed. The
        // By Status breakdown below still shows the DELETED bucket so admins
        // can see how many were removed.
        long activeCount = all.stream()
                .filter(e -> !"DELETED".equalsIgnoreCase(e.getStatus()))
                .count();
        return new EmployeeReport(
                activeCount,
                countBy(all, e -> e.getStatus() != null ? e.getStatus() : "UNKNOWN"),
                countBy(all, e -> e.getDepartment() != null ? e.getDepartment().getName() : "Unassigned"),
                countBy(all, e -> e.getBranch() != null ? e.getBranch().getName() : "Unassigned"),
                countBy(all, e -> e.getEmploymentType() != null ? e.getEmploymentType() : "UNKNOWN")
        );
    }

    @Transactional(readOnly = true)
    public AttendanceReport attendanceReport(LocalDate date) {
        List<Attendance> records = attendanceRepository.findByAttendanceDate(date);
        long checkedIn = records.stream().filter(a -> a.getCheckInTime() != null).count();
        long checkedOut = records.stream().filter(a -> a.getCheckOutTime() != null).count();
        long present = records.stream()
                .filter(a -> a.getStatus() != null
                        && a.getStatus().equalsIgnoreCase("PRESENT"))
                .count();
        long absent = records.stream()
                .filter(a -> a.getStatus() != null
                        && a.getStatus().equalsIgnoreCase("ABSENT"))
                .count();
        long regularized = records.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsRegularized()))
                .count();
        return new AttendanceReport(
                date,
                records.size(),
                countBy(records, a -> a.getStatus() != null ? a.getStatus() : "UNKNOWN"),
                checkedIn,
                checkedOut,
                present,
                absent,
                regularized
        );
    }

    @Transactional(readOnly = true)
    public LeaveReport leaveReport() {
        List<LeaveRequest> all = leaveRequestRepository.findAll();
        BigDecimal totalRequested = all.stream()
                .map(LeaveRequest::getNumberOfDays)
                .filter(d -> d != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalApproved = all.stream()
                .filter(l -> "APPROVED".equals(l.getStatus()))
                .map(LeaveRequest::getNumberOfDays)
                .filter(d -> d != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LeaveReport(
                all.size(),
                countBy(all, l -> l.getStatus() != null ? l.getStatus() : "UNKNOWN"),
                totalRequested,
                totalApproved
        );
    }

    private <T> Map<String, Long> countBy(List<T> list, Function<T, String> classifier) {
        return list.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }
}
