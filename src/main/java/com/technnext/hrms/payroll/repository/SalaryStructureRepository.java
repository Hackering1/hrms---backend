package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.SalaryStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Integer> {
    List<SalaryStructure> findByIsActiveTrue();
}