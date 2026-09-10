package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.SalaryStructureComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalaryStructureComponentRepository extends JpaRepository<SalaryStructureComponent, Integer> {
    List<SalaryStructureComponent> findBySalaryStructureIdOrderByDisplayOrder(Integer salaryStructureId);
    void deleteBySalaryStructureId(Integer salaryStructureId);
}