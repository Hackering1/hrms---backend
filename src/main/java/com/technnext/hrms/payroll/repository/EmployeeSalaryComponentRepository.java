package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.EmployeeSalaryComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeSalaryComponentRepository extends JpaRepository<EmployeeSalaryComponent, Integer> {
    List<EmployeeSalaryComponent> findByEmployeeSalaryId(Integer employeeSalaryId);
    void deleteByEmployeeSalaryId(Integer employeeSalaryId);
}