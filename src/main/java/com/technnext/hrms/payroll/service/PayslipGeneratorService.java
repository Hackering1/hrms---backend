package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.payroll.dto.PayslipResponse;
import com.technnext.hrms.payroll.entity.Payslip;
import com.technnext.hrms.payroll.entity.PayslipComponent;
import com.technnext.hrms.payroll.entity.PayrollRun;
import com.technnext.hrms.payroll.repository.PayrollAdjustmentRepository;
import com.technnext.hrms.payroll.repository.PayrollRunRepository;
import com.technnext.hrms.payroll.repository.PayslipComponentRepository;
import com.technnext.hrms.payroll.repository.PayslipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Payroll → Payslip Generator page: generates ONE employee's payslip inside an
 * already-existing Payroll Run, for the case where PayrollRunService.process()
 * didn't produce one for them (e.g. they were added to payroll after the run
 * was processed). This is a deliberately separate, additive service — it does
 * NOT modify PayrollRunService, which remains the sole owner of the bulk
 * process/approve/mark-paid/cancel run lifecycle.
 *
 * All actual payroll math (Basic/HRA/PF/PT/TDS/gross/net) is delegated to the
 * existing PayrollCalculationService.calculate() — nothing here recalculates
 * or duplicates that logic. Response formatting is delegated to the existing
 * PayslipService.getById(...), so the shape returned here is identical to
 * every other payslip endpoint the frontend already knows how to render.
 *
 * Safety: if a payslip already exists for the (run, employee) pair, this
 * service NEVER deletes or regenerates it — it throws a BadRequestException.
 * The Payslip Generator page is expected to show the existing payslip
 * read-only instead of ever calling this endpoint for a pair that already
 * has one; this check is the server-side backstop for that rule.
 */
@Service
@RequiredArgsConstructor
public class PayslipGeneratorService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipComponentRepository payslipComponentRepository;
    private final PayrollAdjustmentRepository payrollAdjustmentRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollCalculationService calculationService;
    private final PayslipService payslipService;

    @Transactional
    public PayslipResponse generateForEmployee(Integer runId, UUID employeeId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + runId));

        // Same restriction PayrollRunService.process() already enforces — once a run is
        // APPROVED or PAID, its payslips must stop moving; nothing new should land under it.
        if ("APPROVED".equals(run.getStatus()) || "PAID".equals(run.getStatus())) {
            throw new BadRequestException(
                    "Payroll run for " + run.getMonth() + "/" + run.getYear() + " is already "
                            + run.getStatus() + " — payslips can no longer be added to it.");
        }

        // A cancelled run has had its payslips removed on purpose; nothing should land under it
        // (process it again first, which reopens the run).
        if ("CANCELLED".equals(run.getStatus())) {
            throw new BadRequestException(
                    "Payroll run for " + run.getMonth() + "/" + run.getYear()
                            + " is CANCELLED — process the run again before adding payslips to it.");
        }

        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }

        // NEVER silently replace an existing payslip.
        if (payslipRepository.findByPayrollRunIdAndEmployeeId(runId, employeeId).isPresent()) {
            throw new BadRequestException(
                    "A payslip already exists for this employee in this payroll run. "
                            + "View the existing payslip instead of generating a new one.");
        }

        PayrollCalculationService.Result r = calculationService.calculate(employeeId, run.getMonth(), run.getYear());

        Payslip payslip = Payslip.builder()
                .payrollRunId(run.getId())
                .month(run.getMonth())
                .year(run.getYear())
                .employeeId(employeeId)
                .employeeSalaryId(r.employeeSalaryId())
                .workingDays(r.workingDays())
                .paidDays(r.paidDays())
                .lopDays(r.lopDays())
                .grossEarnings(r.grossEarnings())
                .totalDeductions(r.totalDeductions())
                .netPay(r.netPay())
                .employerCost(r.employerCost())
                .pfEmployee(r.pfEmployee())
                .pfEmployer(r.pfEmployer())
                .ptAmount(r.ptAmount())
                .tdsAmount(r.tdsAmount())
                .status("GENERATED")
                .build();
        payslip = payslipRepository.save(payslip);

        int order = 0;
        for (PayrollCalculationService.LineItem line : r.lines()) {
            payslipComponentRepository.save(PayslipComponent.builder()
                    .payslipId(payslip.getId())
                    .componentName(line.name())
                    .componentType(line.type())
                    .amount(line.amount())
                    .displayOrder(order++)
                    .build());
        }

        // Same adjustment bookkeeping PayrollRunService.process() performs: lock the
        // one-off adjustments this payslip consumed against this run so they aren't
        // picked up again by a later generation or a future full re-process.
        for (Integer adjId : r.consumedAdjustmentIds()) {
            payrollAdjustmentRepository.findById(adjId).ifPresent(a -> {
                a.setAppliedInRunId(run.getId());
                payrollAdjustmentRepository.save(a);
            });
        }

        recalculateRunTotals(run);

        return payslipService.getById(payslip.getId());
    }

    /**
     * Re-sums the run's totals from its current full payslip list — the same
     * figures PayrollRunService.process() computes, just derived after the fact
     * instead of accumulated during a batch loop. Deliberately does not touch
     * status/processedBy/processedAt: this call is additive to an
     * already-processed run, not a re-processing event.
     */
    private void recalculateRunTotals(PayrollRun run) {
        List<Payslip> all = payslipRepository.findByPayrollRunId(run.getId());
        BigDecimal totalGross = BigDecimal.ZERO, totalDeductions = BigDecimal.ZERO, totalNet = BigDecimal.ZERO;
        for (Payslip p : all) {
            totalGross = totalGross.add(p.getGrossEarnings());
            totalDeductions = totalDeductions.add(p.getTotalDeductions());
            totalNet = totalNet.add(p.getNetPay());
        }
        run.setTotalGross(totalGross);
        run.setTotalDeductions(totalDeductions);
        run.setTotalNet(totalNet);
        run.setEmployeeCount(all.size());
        payrollRunRepository.save(run);
    }
}