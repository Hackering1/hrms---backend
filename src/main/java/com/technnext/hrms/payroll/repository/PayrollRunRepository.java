package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Integer> {
    Optional<PayrollRun> findByMonthAndYear(Integer month, Integer year);
    List<PayrollRun> findByYearOrderByMonthDesc(Integer year);
    List<PayrollRun> findAllByOrderByYearDescMonthDesc();
}