package com.technnext.hrms.attendance.repository;

import com.technnext.hrms.attendance.entity.AttendanceRegularization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AttendanceRegularizationRepository extends JpaRepository<AttendanceRegularization, Integer> {
    List<AttendanceRegularization> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
    List<AttendanceRegularization> findByStatusOrderByCreatedAtDesc(String status);
}