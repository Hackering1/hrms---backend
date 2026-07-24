package com.technnext.hrms.employee.repository;

import com.technnext.hrms.employee.entity.EmployeeContact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeContactRepository extends JpaRepository<EmployeeContact, Integer> {
    Optional<EmployeeContact> findByEmployeeId(UUID employeeId);
}