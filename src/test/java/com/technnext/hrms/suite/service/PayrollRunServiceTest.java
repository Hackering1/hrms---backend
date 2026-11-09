package com.technnext.hrms.suite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.technnext.hrms.suite.dto.PayrollRunEmployeeInput;
import com.technnext.hrms.suite.dto.PayrollRunRequest;
import com.technnext.hrms.suite.entity.PayrollProfile;
import com.technnext.hrms.suite.entity.SuiteRecord;
import com.technnext.hrms.suite.repository.PayrollProfileRepository;
import com.technnext.hrms.suite.repository.SuiteRecordRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PayrollRunServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void calculatesPayrollAndCreatesPrivatePayslipRecord() throws Exception {
        SuiteRecordRepository records = Mockito.mock(SuiteRecordRepository.class);
        PayrollProfileRepository profiles = Mockito.mock(PayrollProfileRepository.class);
        UUID employeeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        PayrollProfile profile = PayrollProfile.builder()
                .employeeId(employeeId)
                .gross(new BigDecimal("50000.00"))
                .basic(new BigDecimal("25000.00"))
                .hra(new BigDecimal("10000.00"))
                .lta(new BigDecimal("2000.00"))
                .pfRate(new BigDecimal("12.00"))
                .esiRate(BigDecimal.ZERO)
                .professionalTax(new BigDecimal("200.00"))
                .tds(new BigDecimal("500.00"))
                .employerPfRate(new BigDecimal("12.00"))
                .employerEsiRate(BigDecimal.ZERO)
                .gratuity(new BigDecimal("2000.00"))
                .policyVersion("test-policy")
                .build();

        when(profiles.findByEmployeeIdIn(List.of(employeeId))).thenReturn(List.of(profile));
        when(records.existsByModuleAndEmployeeIdAndPeriod("payroll", employeeId, "2026-10"))
                .thenReturn(false);
        when(records.save(any(SuiteRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayrollRunService service = new PayrollRunService(records, profiles);
        List<SuiteRecord> result = service.calculateAndGenerate(
                new PayrollRunRequest("2026-10", List.of(new PayrollRunEmployeeInput(employeeId, 31, 0))), actorId);

        assertEquals(1, result.size());
        assertEquals("payroll", result.get(0).getModule());
        assertEquals("CALCULATED", result.get(0).getStatus());
        assertEquals(new BigDecimal("46300.00"), result.get(0).getAmount());

        ArgumentCaptor<SuiteRecord> saved = ArgumentCaptor.forClass(SuiteRecord.class);
        verify(records, times(2)).save(saved.capture());
        SuiteRecord payslip = saved.getAllValues().get(1);
        assertEquals("payslips", payslip.getModule());
        assertEquals("PENDING_APPROVAL", payslip.getStatus());
        assertEquals(employeeId, payslip.getEmployeeId());
        JsonNode breakdown = JSON.readTree(payslip.getDetails());
        assertEquals(0, new BigDecimal("3000.00").compareTo(breakdown.path("employeePf").decimalValue()));
        assertEquals(0, new BigDecimal("200.00").compareTo(breakdown.path("professionalTax").decimalValue()));
        assertEquals(0, new BigDecimal("46300.00").compareTo(breakdown.path("netPay").decimalValue()));
    }
}
