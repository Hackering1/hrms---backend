package com.technnext.hrms.suite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.suite.entity.PayrollDeletionEvent;
import com.technnext.hrms.suite.entity.SuiteRecord;
import com.technnext.hrms.suite.repository.PayrollDeletionEventRepository;
import com.technnext.hrms.suite.repository.SuiteRecordRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;

class PayrollDeletionServiceTest {
    @Test
    void deletesOldPayrollAndPayslipAndRetainsAuditSnapshot() {
        SuiteRecordRepository records = mock(SuiteRecordRepository.class);
        PayrollDeletionEventRepository events = mock(PayrollDeletionEventRepository.class);
        PayrollDeletionService service = new PayrollDeletionService(records, events);
        UUID payrollId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        SuiteRecord payroll = row("payroll", "APPROVED", payrollId, "2026-10");
        SuiteRecord payslip = row("payslips", "EMAILED", UUID.randomUUID(), "2026-10");
        payslip.setEmployeeId(payroll.getEmployeeId());
        when(records.lockByIdAndModule(payrollId, "payroll")).thenReturn(Optional.of(payroll));
        when(records.lockByModuleAndEmployeeIdAndPeriod("payslips", payroll.getEmployeeId(), "2026-10"))
                .thenReturn(Optional.of(payslip));

        service.deletePayroll(payrollId, actorId);

        ArgumentCaptor<PayrollDeletionEvent> audit = ArgumentCaptor.forClass(PayrollDeletionEvent.class);
        verify(events).save(audit.capture());
        assertEquals(payrollId, audit.getValue().getPayrollRecordId());
        assertEquals(payslip.getId(), audit.getValue().getPayslipRecordId());
        assertEquals(actorId, audit.getValue().getDeletedBy());
        assertEquals(payroll.getEmployeeId(), audit.getValue().getEmployeeId());
        verify(records).deleteAll(java.util.List.of(payslip, payroll));
        verify(records).flush();
    }

    @Test
    void refusesToDeleteTheCurrentPayrollMonth() {
        SuiteRecordRepository records = mock(SuiteRecordRepository.class);
        PayrollDeletionEventRepository events = mock(PayrollDeletionEventRepository.class);
        PayrollDeletionService service = new PayrollDeletionService(records, events);
        YearMonth current = YearMonth.now(ZoneId.of("Asia/Kolkata"));
        UUID payrollId = UUID.randomUUID();
        when(records.lockByIdAndModule(payrollId, "payroll"))
                .thenReturn(Optional.of(row("payroll", "CALCULATED", payrollId, current.toString())));

        assertThrows(BadRequestException.class, () -> service.deletePayroll(payrollId, UUID.randomUUID()));
        verify(events, never()).save(any());
        verify(records, never()).deleteAll(any(java.util.Collection.class));
    }

    private SuiteRecord row(String module, String status, UUID id, String period) {
        return SuiteRecord.builder().id(id).module(module).title(module).status(status)
                .employeeId(UUID.randomUUID()).period(period).amount(new BigDecimal("1000.00"))
                .createdBy(UUID.randomUUID()).build();
    }
}

