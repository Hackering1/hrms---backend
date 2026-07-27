package com.technnext.hrms.employee.repository;

import com.technnext.hrms.employee.entity.EmployeeEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EmployeeEducationRepository extends JpaRepository<EmployeeEducation, Integer> {
    List<EmployeeEducation> findByEmployeeId(UUID employeeId);
    void deleteByEmployeeId(UUID employeeId);
}