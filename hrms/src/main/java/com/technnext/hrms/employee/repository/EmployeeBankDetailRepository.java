package com.technnext.hrms.employee.repository;

import com.technnext.hrms.employee.entity.EmployeeBankDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeBankDetailRepository extends JpaRepository<EmployeeBankDetail, Integer> {
    Optional<EmployeeBankDetail> findByEmployeeId(UUID employeeId);
}