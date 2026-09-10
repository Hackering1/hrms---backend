package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.PayrollAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, Integer> {
    List<PayrollAdjustment> findByEmployeeIdAndYearAndMonth(UUID employeeId, Integer year, Integer month);
    List<PayrollAdjustment> findByYearAndMonthAndAppliedInRunIdIsNull(Integer year, Integer month);
    List<PayrollAdjustment> findByYearAndMonth(Integer year, Integer month);
}