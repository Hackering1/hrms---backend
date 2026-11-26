package com.technnext.hrms.suite.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.suite.entity.PayrollDeletionEvent;
import com.technnext.hrms.suite.entity.SuiteRecord;
import com.technnext.hrms.suite.repository.PayrollDeletionEventRepository;
import com.technnext.hrms.suite.repository.SuiteRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Permanently removes only completed payroll periods and retains a deletion audit tombstone. */
@Service
@RequiredArgsConstructor
public class PayrollDeletionService {
    private final SuiteRecordRepository records;
    private final PayrollDeletionEventRepository deletionEvents;

    @Transactional
    public void deletePayroll(UUID payrollId, UUID actorId) {
        SuiteRecord payroll = records.lockByIdAndModule(payrollId, "payroll")
                .orElseThrow(() -> new ResourceNotFoundException("Payroll record", payrollId));
        YearMonth period;
        try {
            period = YearMonth.parse(payroll.getPeriod());
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new BadRequestException("Payroll period is invalid; this record cannot be deleted.");
        }
        if (!period.isBefore(YearMonth.now(ZoneId.of("Asia/Kolkata"))))
            throw new BadRequestException("Only payroll from a completed month can be permanently deleted.");
        if ("EMAIL_SENDING".equals(payroll.getStatus()))
            throw new BadRequestException("Payroll cannot be deleted while its payslip email is being sent.");

        List<SuiteRecord> affected = new ArrayList<>();
        SuiteRecord payslip = records.lockByModuleAndEmployeeIdAndPeriod("payslips", payroll.getEmployeeId(), payroll.getPeriod())
                .orElse(null);
        if (payslip != null) {
            if ("EMAIL_SENDING".equals(payslip.getStatus()))
                throw new BadRequestException("Payroll cannot be deleted while its payslip email is being sent.");
            affected.add(payslip);
        }
        affected.add(payroll);

        deletionEvents.save(PayrollDeletionEvent.builder()
                .payrollRecordId(payroll.getId())
                .payslipRecordId(payslip == null ? null : payslip.getId())
                .employeeId(payroll.getEmployeeId())
                .period(payroll.getPeriod())
                .payrollStatus(payroll.getStatus())
                .payslipStatus(payslip == null ? null : payslip.getStatus())
                .payrollAmount(payroll.getAmount())
                .payslipAmount(payslip == null ? null : payslip.getAmount())
                .deletedBy(actorId)
                .build());

        records.deleteAll(affected);
        records.flush();
    }
}

