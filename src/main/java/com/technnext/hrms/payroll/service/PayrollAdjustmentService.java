package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.payroll.dto.PayrollAdjustmentRequest;
import com.technnext.hrms.payroll.entity.PayrollAdjustment;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.payroll.repository.PayrollAdjustmentRepository;
import com.technnext.hrms.payroll.repository.PayrollRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayrollAdjustmentService {

    private final PayrollAdjustmentRepository repository;
    private final PayrollRunRepository payrollRunRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public List<PayrollAdjustment> byEmployeeAndMonth(UUID employeeId, int year, int month) {
        return repository.findByEmployeeIdAndYearAndMonth(employeeId, year, month);
    }

    @Transactional
    public PayrollAdjustment create(PayrollAdjustmentRequest req, UUID createdBy) {
        if (!"EARNING".equals(req.adjustmentType()) && !"DEDUCTION".equals(req.adjustmentType())) {
            throw new BadRequestException("adjustmentType must be EARNING or DEDUCTION.");
        }
        // The sign is carried by adjustmentType; a negative/zero amount would silently reverse it
        // (a negative EARNING lowers pay, a negative DEDUCTION raises it).
        if (req.amount().signum() <= 0) {
            throw new BadRequestException("Amount must be greater than 0. Use the type (EARNING / DEDUCTION) to set the direction.");
        }
        if (req.month() < 1 || req.month() > 12) {
            throw new BadRequestException("month must be between 1 and 12.");
        }
        if (req.year() < 2000 || req.year() > 2100) {
            throw new BadRequestException("year is out of range.");
        }
        if (!employeeRepository.existsById(req.employeeId())) {
            throw new ResourceNotFoundException("Employee not found: " + req.employeeId());
        }
        // An APPROVED/PAID run never reprocesses, so an adjustment added now would never be applied
        // and would sit unapplied forever.
        payrollRunRepository.findByMonthAndYear(req.month(), req.year()).ifPresent(run -> {
            if ("APPROVED".equals(run.getStatus()) || "PAID".equals(run.getStatus())) {
                throw new BadRequestException("The payroll run for " + req.month() + "/" + req.year()
                        + " is already " + run.getStatus() + ", so a new adjustment can no longer be applied to it.");
            }
        });
        PayrollAdjustment adj = PayrollAdjustment.builder()
                .employeeId(req.employeeId())
                .month(req.month())
                .year(req.year())
                .adjustmentType(req.adjustmentType())
                .label(req.label())
                .amount(req.amount())
                .isTaxable(req.isTaxable() == null ? true : req.isTaxable())
                .remarks(req.remarks())
                .createdBy(createdBy)
                .build();
        return repository.save(adj);
    }

    @Transactional
    public void delete(Integer id) {
        PayrollAdjustment adj = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Adjustment not found: " + id));
        if (adj.getAppliedInRunId() != null) {
            throw new BadRequestException("This adjustment has already been applied to a payroll run and can no longer be deleted. Cancel that run first if it needs to change.");
        }
        repository.deleteById(id);
    }
}