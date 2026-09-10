package com.technnext.hrms.leave.repository;

import com.technnext.hrms.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Integer> {
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);

    // Payroll: an employee's APPROVED leave requests overlapping a payroll month,
    // to count paid-leave days (and, by exclusion, unapproved absence -> LOP).
    @org.springframework.data.jpa.repository.Query("""
        SELECT lr FROM LeaveRequest lr
        WHERE lr.employeeId = :employeeId
          AND lr.status = 'APPROVED'
          AND lr.fromDate <= :to
          AND lr.toDate >= :from
        """)
    List<LeaveRequest> findApprovedOverlapping(UUID employeeId, LocalDate from, LocalDate to);
}