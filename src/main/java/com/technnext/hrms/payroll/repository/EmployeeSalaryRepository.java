package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.EmployeeSalary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeSalaryRepository extends JpaRepository<EmployeeSalary, Integer> {
    List<EmployeeSalary> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

    // The assignment active for a given date: effectiveFrom <= date AND (effectiveTo IS NULL OR effectiveTo >= date)
    @org.springframework.data.jpa.repository.Query("""
        SELECT es FROM EmployeeSalary es
        WHERE es.employeeId = :employeeId
          AND es.effectiveFrom <= :asOf
          AND (es.effectiveTo IS NULL OR es.effectiveTo >= :asOf)
        ORDER BY es.effectiveFrom DESC
        """)
    List<EmployeeSalary> findActiveAsOf(UUID employeeId, LocalDate asOf);

    default Optional<EmployeeSalary> findCurrentActive(UUID employeeId, LocalDate asOf) {
        List<EmployeeSalary> rows = findActiveAsOf(employeeId, asOf);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    // Every employee with a salary row that is (or was) active as-of the given date —
    // used to determine who to include when a payroll run for that month is processed.
    @org.springframework.data.jpa.repository.Query("""
        SELECT DISTINCT es.employeeId FROM EmployeeSalary es
        WHERE es.effectiveFrom <= :asOf
          AND (es.effectiveTo IS NULL OR es.effectiveTo >= :asOf)
        """)
    List<UUID> findAllEmployeeIdsActiveAsOf(LocalDate asOf);
}