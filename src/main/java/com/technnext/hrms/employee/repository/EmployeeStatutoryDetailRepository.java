package com.technnext.hrms.employee.repository;

import com.technnext.hrms.employee.entity.EmployeeStatutoryDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeStatutoryDetailRepository extends JpaRepository<EmployeeStatutoryDetail, Integer> {
    Optional<EmployeeStatutoryDetail> findByEmployeeId(UUID employeeId);
}