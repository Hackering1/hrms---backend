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

    // Same, but only payslips strictly BEFORE the given payroll month — "withheld prior to this
    // run". The query above also counts the run's own month and any later months, which skews
    // the projection when an earlier month is re-processed.
    @org.springframework.data.jpa.repository.Query("""
        SELECT COALESCE(SUM(p.tdsAmount), 0) FROM Payslip p
        WHERE p.employeeId = :employeeId
          AND (
            (p.year = :fyStartYear AND p.month >= 4)
            OR (p.year = :fyEndYear AND p.month <= 3)
          )
          AND (p.year < :beforeYear OR (p.year = :beforeYear AND p.month < :beforeMonth))
        """)
    java.math.BigDecimal sumTdsForFinancialYearBefore(
            @org.springframework.data.repository.query.Param("employeeId") UUID employeeId,
            @org.springframework.data.repository.query.Param("fyStartYear") Integer fyStartYear,
            @org.springframework.data.repository.query.Param("fyEndYear") Integer fyEndYear,
            @org.springframework.data.repository.query.Param("beforeYear") Integer beforeYear,
            @org.springframework.data.repository.query.Param("beforeMonth") Integer beforeMonth);
}