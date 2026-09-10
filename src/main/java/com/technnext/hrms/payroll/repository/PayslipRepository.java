package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayslipRepository extends JpaRepository<Payslip, Integer> {
    List<Payslip> findByPayrollRunId(Integer payrollRunId);
    List<Payslip> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
    Optional<Payslip> findByPayrollRunIdAndEmployeeId(Integer payrollRunId, UUID employeeId);
    void deleteByPayrollRunId(Integer payrollRunId);

    // Sum of TDS already withheld this financial year (Apr..month-1) for the
    // running annualized TDS projection. financial year runs Apr(4) -> Mar(3).
    @org.springframework.data.jpa.repository.Query("""
        SELECT COALESCE(SUM(p.tdsAmount), 0) FROM Payslip p
        WHERE p.employeeId = :employeeId
          AND (
            (p.year = :fyStartYear AND p.month >= 4)
            OR (p.year = :fyEndYear AND p.month <= 3)
          )
        """)
    java.math.BigDecimal sumTdsForFinancialYearSoFar(UUID employeeId, Integer fyStartYear, Integer fyEndYear);
}