package com.technnext.hrms.suite.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.suite.entity.SuiteRecord;
import com.technnext.hrms.suite.repository.SuiteRecordArchiveEventRepository;
import com.technnext.hrms.suite.repository.SuiteRecordRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class PayrollArchiveServiceTest {
    @Test
    void archivesPayrollAndRelatedPayslipWithActorAndCanRestoreBoth() {
        SuiteRecordRepository repository = mock(SuiteRecordRepository.class);
        SuiteRecordArchiveEventRepository events = mock(SuiteRecordArchiveEventRepository.class);
        PayrollArchiveService service = new PayrollArchiveService(repository, events);
        UUID payrollId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        SuiteRecord payroll = row("payroll", "APPROVED");
        SuiteRecord payslip = row("payslips", "EMAILED");
        payslip.setEmployeeId(payroll.getEmployeeId());
        when(repository.lockByIdAndModule(payrollId, "payroll")).thenReturn(Optional.of(payroll));
        when(repository.lockByModuleAndEmployeeIdAndPeriod("payslips", payroll.getEmployeeId(), payroll.getPeriod()))
                .thenReturn(Optional.of(payslip));
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.archive(payrollId, actorId);

        assertNotNull(payroll.getArchivedAt());
        assertNotNull(payslip.getArchivedAt());
        assertEquals(actorId, payroll.getArchivedBy());
        assertEquals(actorId, payslip.getArchivedBy());

        service.restore(payrollId, actorId);

        assertNull(payroll.getArchivedAt());
        assertNull(payroll.getArchivedBy());
        assertNull(payslip.getArchivedAt());
        assertNull(payslip.getArchivedBy());
        verify(repository, times(2)).saveAll(anyList());
        verify(events, times(4)).save(any());
    }

    @Test
    void refusesToArchiveWhilePayslipIsBeingEmailed() {
        SuiteRecordRepository repository = mock(SuiteRecordRepository.class);
        SuiteRecordArchiveEventRepository events = mock(SuiteRecordArchiveEventRepository.class);
        PayrollArchiveService service = new PayrollArchiveService(repository, events);
        UUID payrollId = UUID.randomUUID();
        SuiteRecord payroll = row("payroll", "CALCULATED");
        SuiteRecord payslip = row("payslips", "EMAIL_SENDING");
        payslip.setEmployeeId(payroll.getEmployeeId());
        when(repository.lockByIdAndModule(payrollId, "payroll")).thenReturn(Optional.of(payroll));
        when(repository.lockByModuleAndEmployeeIdAndPeriod("payslips", payroll.getEmployeeId(), payroll.getPeriod()))
                .thenReturn(Optional.of(payslip));

        assertThrows(BadRequestException.class, () -> service.archive(payrollId, UUID.randomUUID()));
        assertNull(payroll.getArchivedAt());
        assertNull(payslip.getArchivedAt());
        verify(repository, never()).saveAll(anyList());
        verify(events, never()).save(any());
    }

    @Test
    void refusesToArchiveTheCurrentPayrollMonth() {
        SuiteRecordRepository repository = mock(SuiteRecordRepository.class);
        SuiteRecordArchiveEventRepository events = mock(SuiteRecordArchiveEventRepository.class);
        PayrollArchiveService service = new PayrollArchiveService(repository, events);
        UUID payrollId = UUID.randomUUID();
        SuiteRecord payroll = row("payroll", "CALCULATED");
        payroll.setPeriod(YearMonth.now(ZoneId.of("Asia/Kolkata")).toString());
        when(repository.lockByIdAndModule(payrollId, "payroll")).thenReturn(Optional.of(payroll));

        assertThrows(BadRequestException.class, () -> service.archive(payrollId, UUID.randomUUID()));
        verify(repository, never()).saveAll(anyList());
        verify(events, never()).save(any());
    }

    private SuiteRecord row(String module, String status) {
        return SuiteRecord.builder().module(module).title(module).status(status)
                .employeeId(UUID.randomUUID()).period("2026-10").createdBy(UUID.randomUUID()).build();
    }
}

