package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.PayslipComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PayslipComponentRepository extends JpaRepository<PayslipComponent, Integer> {
    List<PayslipComponent> findByPayslipIdOrderByDisplayOrder(Integer payslipId);
}