package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.SalaryComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalaryComponentRepository extends JpaRepository<SalaryComponent, Integer> {
    List<SalaryComponent> findByIsActiveTrue();
}