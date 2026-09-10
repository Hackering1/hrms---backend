package com.technnext.hrms.payroll.service;

import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.payroll.entity.*;
import com.technnext.hrms.payroll.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes ONE employee's payslip line-items for a given month. Called by
 * PayrollRunService once per employee while processing a run. Does not save
 * anything itself — returns a fully-computed Result for the caller to persist,
 * so a "preview before processing" endpoint can reuse the same logic later
 * without side effects.
 */
@Service
@RequiredArgsConstructor
public class PayrollCalculationService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSalaryRepository employeeSalaryRepository;
    private final EmployeeSalaryComponentRepository employeeSalaryComponentRepository;
    private final SalaryComponentRepository salaryComponentRepository;
    private final PayrollAdjustmentRepository payrollAdjustmentRepository;
    private final PayslipRepository payslipRepository;
    private final LopCalculator lopCalculator;
    private final PfCalculator pfCalculator;
    private final PtCalculator ptCalculator;
    private final TdsCalculator tdsCalculator;

    public record LineItem(String name, String type, BigDecimal amount) {}

    public record Result(
            UUID employeeId,
            Integer employeeSalaryId,
            BigDecimal workingDays,
            BigDecimal paidDays,
            BigDecimal lopDays,
            BigDecimal grossEarnings,
            BigDecimal totalDeductions,
            BigDecimal netPay,
            BigDecimal employerCost,
            BigDecimal pfEmployee,
            BigDecimal pfEmployer,
            BigDecimal ptAmount,
            BigDecimal tdsAmount,
            List<LineItem> lines,
            List<Integer> consumedAdjustmentIds,
            boolean needsManualTdsReview
    ) {}

    public Result calculate(UUID employeeId, int month, int year) {
        EmployeeSalary salary = employeeSalaryRepository
                .findCurrentActive(employeeId, java.time.YearMonth.of(year, month).atEndOfMonth())
                .orElseThrow(() -> new com.technnext.hrms.common.exception.BadRequestException(
                        "No active salary structure for employee " + employeeId + " as of " + year + "-" + month));

        Employee employee = employeeRepository.findById(employeeId).orElse(null);

        List<EmployeeSalaryComponent> breakupRows = employeeSalaryComponentRepository.findByEmployeeSalaryId(salary.getId());
        Map<Integer, SalaryComponent> componentsById = salaryComponentRepository.findAllById(
                breakupRows.stream().map(EmployeeSalaryComponent::getSalaryComponentId).toList()
        ).stream().collect(Collectors.toMap(SalaryComponent::getId, c -> c));

        LopCalculator.LopResult lop = lopCalculator.calculate(employeeId, month, year);
        BigDecimal prorationFactor = lop.workingDays().signum() == 0
                ? BigDecimal.ONE
                : lop.paidDays().divide(lop.workingDays(), 6, RoundingMode.HALF_UP);

        java.util.List<LineItem> lines = new java.util.ArrayList<>();
        BigDecimal grossEarnings = BigDecimal.ZERO;
        BigDecimal taxableGross = BigDecimal.ZERO;
        BigDecimal proratedBasic = BigDecimal.ZERO;

        for (EmployeeSalaryComponent row : breakupRows) {
            SalaryComponent c = componentsById.get(row.getSalaryComponentId());
            if (c == null || !"EARNING".equals(c.getComponentType())) continue;
            BigDecimal prorated = row.getMonthlyAmount().multiply(prorationFactor).setScale(2, RoundingMode.HALF_UP);
            lines.add(new LineItem(c.getName(), "EARNING", prorated));
            grossEarnings = grossEarnings.add(prorated);
            if (Boolean.TRUE.equals(c.getIsTaxable())) taxableGross = taxableGross.add(prorated);
            if ("BASIC".equals(c.getCode())) proratedBasic = prorated;
        }

        // One-off adjustments for this employee/month not yet consumed by another run.
        List<PayrollAdjustment> adjustments = payrollAdjustmentRepository
                .findByEmployeeIdAndYearAndMonth(employeeId, year, month).stream()
                .filter(a -> a.getAppliedInRunId() == null)
                .toList();
        BigDecimal adjustmentEarnings = BigDecimal.ZERO;
        BigDecimal adjustmentDeductions = BigDecimal.ZERO;
        for (PayrollAdjustment adj : adjustments) {
            if ("EARNING".equals(adj.getAdjustmentType())) {
                lines.add(new LineItem(adj.getLabel(), "EARNING", adj.getAmount()));
                adjustmentEarnings = adjustmentEarnings.add(adj.getAmount());
                if (Boolean.TRUE.equals(adj.getIsTaxable())) taxableGross = taxableGross.add(adj.getAmount());
            } else {
                lines.add(new LineItem(adj.getLabel(), "DEDUCTION", adj.getAmount()));
                adjustmentDeductions = adjustmentDeductions.add(adj.getAmount());
            }
        }
        grossEarnings = grossEarnings.add(adjustmentEarnings);

        // ── Statutory deductions ──────────────────────────────────────────
        PfCalculator.PfResult pf = pfCalculator.calculate(proratedBasic, Boolean.TRUE.equals(salary.getPfApplicable()));
        if (pf.employeeContribution().signum() > 0) lines.add(new LineItem("Provident Fund (Employee)", "DEDUCTION", pf.employeeContribution()));
        if (pf.employerContribution().signum() > 0) lines.add(new LineItem("Provident Fund (Employer)", "EMPLOYER_CONTRIBUTION", pf.employerContribution()));

        String state = employee != null && employee.getBranch() != null ? employee.getBranch().getState() : null;
        String gender = employee != null ? employee.getGender() : null;
        BigDecimal pt = ptCalculator.calculate(state, gender, grossEarnings, month, Boolean.TRUE.equals(salary.getPtApplicable()));
        if (pt.signum() > 0) lines.add(new LineItem("Professional Tax", "DEDUCTION", pt));

        boolean needsManualTdsReview = false;
        BigDecimal tds;
        if (salary.getTdsOverrideMonthly() != null) {
            tds = salary.getTdsOverrideMonthly();
        } else if ("NEW".equals(salary.getTaxRegime())) {
            int fyStart = month >= 4 ? year : year - 1;
            BigDecimal ytdTds = payslipRepository.sumTdsForFinancialYearSoFar(employeeId, fyStart, fyStart + 1);
            tds = tdsCalculator.calculateMonthlyTds(taxableGross, ytdTds, month, year);
        } else {
            // OLD regime with no manual override set — cannot compute without investment
            // declarations (out of scope). Zero, but flag for HR to review before approval.
            tds = BigDecimal.ZERO;
            needsManualTdsReview = true;
        }
        if (tds.signum() > 0) lines.add(new LineItem("Income Tax (TDS)", "DEDUCTION", tds));

        BigDecimal totalDeductions = pf.employeeContribution().add(pt).add(tds).add(adjustmentDeductions);
        BigDecimal netPay = grossEarnings.subtract(totalDeductions);
        BigDecimal employerCost = grossEarnings.add(pf.employerContribution());

        return new Result(
                employeeId, salary.getId(),
                lop.workingDays(), lop.paidDays(), lop.lopDays(),
                grossEarnings.setScale(2, RoundingMode.HALF_UP),
                totalDeductions.setScale(2, RoundingMode.HALF_UP),
                netPay.setScale(2, RoundingMode.HALF_UP),
                employerCost.setScale(2, RoundingMode.HALF_UP),
                pf.employeeContribution(), pf.employerContribution(), pt, tds,
                lines,
                adjustments.stream().map(PayrollAdjustment::getId).toList(),
                needsManualTdsReview
        );
    }
}