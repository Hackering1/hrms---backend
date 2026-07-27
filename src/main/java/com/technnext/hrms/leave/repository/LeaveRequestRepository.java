package com.technnext.hrms.leave.repository;

import com.technnext.hrms.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Integer> {
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
    List<LeaveRequest> findByStatusOrderByCreatedAtDesc(String status);
}