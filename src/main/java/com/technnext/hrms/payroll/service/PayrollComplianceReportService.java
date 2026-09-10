package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.payroll.dto.PayrollComplianceReport;
import com.technnext.hrms.payroll.entity.PayrollRun;
import com.technnext.hrms.payroll.entity.Payslip;
import com.technnext.hrms.payroll.repository.PayrollRunRepository;
import com.technnext.hrms.payroll.repository.PayslipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** PF / PT / TDS compliance summaries for a processed payroll run — the numbers an employer files with EPFO / the state PT department / TRACES. */
@Service
@RequiredArgsConstructor
public class PayrollComplianceReportService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public PayrollComplianceReport forRun(Integer payrollRunId) {
        PayrollRun run = payrollRunRepository.findById(payrollRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + payrollRunId));
        List<Payslip> payslips = payslipRepository.findByPayrollRunId(payrollRunId);

        Map<java.util.UUID, Employee> employeesById = employeeRepository.findAllById(
                payslips.stream().map(Payslip::getEmployeeId).toList()
        ).stream().collect(Collectors.toMap(Employee::getId, e -> e));

        List<PayrollComplianceReport.Row> rows = payslips.stream().map(p -> {
            Employee e = employeesById.get(p.getEmployeeId());
            return new PayrollComplianceReport.Row(
                    p.getEmployeeId(),
                    e != null ? e.getEmployeeCode() : null,
                    e != null ? e.getFirstName() + " " + e.getLastName() : null,
                    p.getGrossEarnings(), p.getPfEmployee(), p.getPfEmployer(), p.getPtAmount(), p.getTdsAmount(), p.getNetPay()
            );
        }).collect(Collectors.toList());

        BigDecimal totalPfEmployee = payslips.stream().map(Payslip::getPfEmployee).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPfEmployer = payslips.stream().map(Payslip::getPfEmployer).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPt = payslips.stream().map(Payslip::getPtAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTds = payslips.stream().map(Payslip::getTdsAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PayrollComplianceReport(run.getId(), run.getMonth(), run.getYear(), totalPfEmployee, totalPfEmployer, totalPt, totalTds, rows);
    }
}