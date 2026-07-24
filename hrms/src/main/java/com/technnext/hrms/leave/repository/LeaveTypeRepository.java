package com.technnext.hrms.leave.repository;

import com.technnext.hrms.leave.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Integer> {
    boolean existsByCode(String code);
}