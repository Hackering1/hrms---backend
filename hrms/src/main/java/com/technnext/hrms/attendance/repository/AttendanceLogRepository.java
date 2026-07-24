package com.technnext.hrms.attendance.repository;

import com.technnext.hrms.attendance.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Integer> {
    List<AttendanceLog> findByEmployeeIdOrderByLogTimeDesc(UUID employeeId);
}