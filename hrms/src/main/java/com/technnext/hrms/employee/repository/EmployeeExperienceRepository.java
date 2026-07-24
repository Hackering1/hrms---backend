package com.technnext.hrms.employee.repository;

import com.technnext.hrms.employee.entity.EmployeeExperience;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EmployeeExperienceRepository extends JpaRepository<EmployeeExperience, Integer> {
    List<EmployeeExperience> findByEmployeeId(UUID employeeId);
    void deleteByEmployeeId(UUID employeeId);
}