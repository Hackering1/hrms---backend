package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.payroll.dto.PayrollAdjustmentRequest;
import com.technnext.hrms.payroll.entity.PayrollAdjustment;
import com.technnext.hrms.payroll.repository.PayrollAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayrollAdjustmentService {

    private final PayrollAdjustmentRepository repository;

    @Transactional(readOnly = true)
    public List<PayrollAdjustment> byEmployeeAndMonth(UUID employeeId, int year, int month) {
        return repository.findByEmployeeIdAndYearAndMonth(employeeId, year, month);
    }

    @Transactional
    public PayrollAdjustment create(PayrollAdjustmentRequest req, UUID createdBy) {
        if (!"EARNING".equals(req.adjustmentType()) && !"DEDUCTION".equals(req.adjustmentType())) {
            throw new BadRequestException("adjustmentType must be EARNING or DEDUCTION.");
        }
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