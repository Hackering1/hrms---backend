package com.technnext.hrms.suite.service;

import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.suite.entity.SuiteRecord;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayslipPdfServiceTest {
    @Test
    void generatesAReadablePayslipPdfWithEmployeeAndPayrollDetails() throws Exception {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        UUID employeeId = UUID.randomUUID();
        Employee employee = Employee.builder()
                .id(employeeId).employeeCode("TN-008").firstName("Srija").lastName("Kankati")
                .email("srija@example.com").bankName("Axis Bank").bankAccountNumber("1234567890")
                .uanNumber("100000000000").panNumber("ABCDE1234F")
                .build();
        when(employees.findById(employeeId)).thenReturn(Optional.of(employee));
        SuiteRecord payroll = SuiteRecord.builder()
                .module("payroll").employeeId(employeeId).period("2026-10")
                .details("{\"paidDays\":30,\"lopDays\":1,\"basic\":13032,\"hra\":5213,\"lta\":652,\"fixedAllowance\":6799,\"performanceBonusEnabled\":true,\"performanceBonus\":1000,\"gross\":26696,\"employeePf\":1564,\"employeeEsi\":0,\"professionalTax\":200,\"tds\":0,\"netPay\":24932}")
                .build();

        var document = new PayslipPdfService(employees).generate(payroll);
        Path pdf = Path.of("target", "payslip-layout-preview.pdf");
        Files.createDirectories(pdf.getParent());
        Files.write(pdf, document.bytes());

        assertTrue(document.bytes().length > 1_000);
        assertTrue(new String(document.bytes(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"));
        assertTrue(Files.size(pdf) > 1_000);
    }
}

