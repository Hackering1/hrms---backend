package com.technnext.hrms.employee.repository;

import com.technnext.hrms.employee.entity.EmployeeManager;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EmployeeManagerRepository extends JpaRepository<EmployeeManager, Integer> {
    List<EmployeeManager> findByManagerId(UUID managerId);
    List<EmployeeManager> findByEmployeeId(UUID employeeId);
}