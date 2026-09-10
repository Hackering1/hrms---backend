package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.email.PayslipEmailEvent;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.payroll.dto.PayrollRunResponse;
import com.technnext.hrms.payroll.entity.*;
import com.technnext.hrms.payroll.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns the payroll run lifecycle: DRAFT (created) -> PROCESSED (payslips
 * generated) -> APPROVED -> PAID. A PROCESSED run can be re-processed (e.g.
 * after fixing an employee's salary structure) as long as it isn't APPROVED
 * yet — approval is the line after which numbers should stop moving.
 */
@Service
@RequiredArgsConstructor
public class PayrollRunService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipComponentRepository payslipComponentRepository;
    private final EmployeeSalaryRepository employeeSalaryRepository;
    private final PayrollAdjustmentRepository payrollAdjustmentRepository;
    private final PayrollCalculationService calculationService;
    private final PayslipPdfService payslipPdfService;
    private final EmployeeRepository employeeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<PayrollRunResponse> list() {
        return payrollRunRepository.findAllByOrderByYearDescMonthDesc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayrollRunResponse getById(Integer id) {
        return toResponse(getEntity(id));
    }

    private PayrollRun getEntity(Integer id) {
        return payrollRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + id));
    }

    /**
     * Creates (if needed) and processes the run for month/year: computes a
     * payslip for every employee with an active salary assignment. Safe to
     * call again on a PROCESSED (not yet APPROVED) run — it recomputes from
     * scratch, replacing the previous payslips.
     */
    @Transactional
    public PayrollRunResponse process(int month, int year, UUID processedBy) {
        PayrollRun run = payrollRunRepository.findByMonthAndYear(month, year)
                .orElseGet(() -> payrollRunRepository.save(PayrollRun.builder().month(month).year(year).status("DRAFT").build()));

        if ("APPROVED".equals(run.getStatus()) || "PAID".equals(run.getStatus())) {
            throw new BadRequestException("Payroll run for " + month + "/" + year + " is already " + run.getStatus() + " and can no longer be reprocessed. Cancel it first if changes are needed.");
        }

        // Clean slate: drop any payslips from a previous processing attempt for this run,
        // and release any adjustments they'd consumed so they're picked up again below.
        List<Payslip> existing = payslipRepository.findByPayrollRunId(run.getId());
        for (Payslip p : existing) {
            payslipComponentRepository.findByPayslipIdOrderByDisplayOrder(p.getId())
                    .forEach(c -> payslipComponentRepository.deleteById(c.getId()));
        }
        payslipRepository.deleteByPayrollRunId(run.getId());
        payrollAdjustmentRepository.findByYearAndMonth(year, month).stream()
                .filter(a -> run.getId().equals(a.getAppliedInRunId()))
                .forEach(a -> { a.setAppliedInRunId(null); payrollAdjustmentRepository.save(a); });

        List<UUID> employeeIds = employeeSalaryRepository.findAllEmployeeIdsActiveAsOf(YearMonth.of(year, month).atEndOfMonth());

        BigDecimal totalGross = BigDecimal.ZERO, totalDeductions = BigDecimal.ZERO, totalNet = BigDecimal.ZERO;
        int count = 0;
        StringBuilder warnings = new StringBuilder();

        for (UUID employeeId : employeeIds) {
            PayrollCalculationService.Result r;
            try {
                r = calculationService.calculate(employeeId, month, year);
            } catch (BadRequestException ex) {
                // Shouldn't normally happen since we sourced employeeIds from active salaries,
                // but don't let one bad record fail the whole run — skip and note it.
                warnings.append(employeeId).append(": ").append(ex.getMessage()).append("; ");
                continue;
            }

            Payslip payslip = Payslip.builder()
                    .payrollRunId(run.getId())
                    .month(month)
                    .year(year)
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

            for (Integer adjId : r.consumedAdjustmentIds()) {
                payrollAdjustmentRepository.findById(adjId).ifPresent(a -> {
                    a.setAppliedInRunId(run.getId());
                    payrollAdjustmentRepository.save(a);
                });
            }

            if (r.needsManualTdsReview()) {
                warnings.append(employeeId).append(": OLD tax regime with no TDS override set (defaulted to Rs.0 — needs manual review); ");
            }

            totalGross = totalGross.add(r.grossEarnings());
            totalDeductions = totalDeductions.add(r.totalDeductions());
            totalNet = totalNet.add(r.netPay());
            count++;
        }

        run.setStatus("PROCESSED");
        run.setTotalGross(totalGross);
        run.setTotalDeductions(totalDeductions);
        run.setTotalNet(totalNet);
        run.setEmployeeCount(count);
        run.setProcessedBy(processedBy);
        run.setProcessedAt(LocalDateTime.now());
        if (!warnings.isEmpty()) run.setRemarks(warnings.toString());
        payrollRunRepository.save(run);

        return toResponse(run);
    }

    @Transactional
    public PayrollRunResponse approve(Integer id, UUID approvedBy) {
        PayrollRun run = getEntity(id);
        if (!"PROCESSED".equals(run.getStatus())) {
            throw new BadRequestException("Only a PROCESSED run can be approved (current status: " + run.getStatus() + ").");
        }
        run.setStatus("APPROVED");
        run.setApprovedBy(approvedBy);
        run.setApprovedAt(LocalDateTime.now());
        return toResponse(payrollRunRepository.save(run));
    }

    @Transactional
    public PayrollRunResponse markPaid(Integer id) {
        PayrollRun run = getEntity(id);
        if (!"APPROVED".equals(run.getStatus())) {
            throw new BadRequestException("Only an APPROVED run can be marked PAID (current status: " + run.getStatus() + ").");
        }
        run.setStatus("PAID");
        run.setPaidAt(LocalDateTime.now());
        payrollRunRepository.save(run);

        List<Payslip> payslips = payslipRepository.findByPayrollRunId(id);
        payslips.forEach(p -> { p.setStatus("PAID"); payslipRepository.save(p); });

        // One payslip email per employee, fired only AFTER this transaction commits
        // (see PayslipEmailEvent / EmployeeWelcomeEmailListener.onPayslipEmail) — if
        // marking-paid somehow rolled back, no emails would go out for it.
        String monthLabel = java.time.LocalDate.of(run.getYear(), run.getMonth(), 1)
                .getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        java.util.Map<UUID, Employee> employeesById = employeeRepository.findAllById(
                payslips.stream().map(Payslip::getEmployeeId).toList()
        ).stream().collect(Collectors.toMap(Employee::getId, e -> e));

        for (Payslip p : payslips) {
            Employee e = employeesById.get(p.getEmployeeId());
            if (e == null || e.getEmail() == null || e.getEmail().isBlank()) continue; // EmailService also guards this, but skip the PDF render if we already know we can't send
            byte[] pdf;
            try {
                pdf = payslipPdfService.generate(p);
            } catch (Exception ex) {
                // A PDF render failure for one employee must not block emailing
                // everyone else, or the "mark paid" action itself.
                continue;
            }
            eventPublisher.publishEvent(new PayslipEmailEvent(
                    e.getEmail(),
                    (e.getFirstName() + " " + e.getLastName()).trim(),
                    e.getEmployeeCode(),
                    monthLabel,
                    run.getYear(),
                    formatMoney(p.getNetPay()),
                    pdf
            ));
        }

        return toResponse(run);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return String.format(Locale.ENGLISH, "%,.2f", amount);
    }

    @Transactional
    public void cancel(Integer id) {
        PayrollRun run = getEntity(id);
        if ("PAID".equals(run.getStatus())) {
            throw new BadRequestException("A PAID run cannot be cancelled.");
        }
        payrollAdjustmentRepository.findByYearAndMonth(run.getYear(), run.getMonth()).stream()
                .filter(a -> id.equals(a.getAppliedInRunId()))
                .forEach(a -> { a.setAppliedInRunId(null); payrollAdjustmentRepository.save(a); });
        payslipRepository.deleteByPayrollRunId(id);
        run.setStatus("CANCELLED");
        payrollRunRepository.save(run);
    }

    private PayrollRunResponse toResponse(PayrollRun r) {
        return new PayrollRunResponse(
                r.getId(), r.getMonth(), r.getYear(), r.getStatus(),
                r.getTotalGross(), r.getTotalDeductions(), r.getTotalNet(), r.getEmployeeCount(),
                r.getProcessedAt(), r.getApprovedAt(), r.getPaidAt(), r.getRemarks()
        );
    }
}