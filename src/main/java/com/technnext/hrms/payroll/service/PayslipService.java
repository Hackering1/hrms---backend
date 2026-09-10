package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.email.PayslipEmailEvent;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.payroll.dto.PayslipResponse;
import com.technnext.hrms.payroll.entity.Payslip;
import com.technnext.hrms.payroll.entity.PayslipComponent;
import com.technnext.hrms.payroll.repository.PayslipComponentRepository;
import com.technnext.hrms.payroll.repository.PayslipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayslipService {

    private final PayslipRepository payslipRepository;
    private final PayslipComponentRepository payslipComponentRepository;
    private final EmployeeRepository employeeRepository;
    private final PayslipPdfService payslipPdfService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<PayslipResponse> byRun(Integer payrollRunId) {
        return payslipRepository.findByPayrollRunId(payrollRunId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PayslipResponse> byEmployee(UUID employeeId) {
        return payslipRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayslipResponse getById(Integer id) {
        Payslip p = payslipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found: " + id));
        return toResponse(p);
    }

    /** Manually re-sends a single payslip email — for when the automatic send (triggered by "Mark Paid") failed or needs a resend to a corrected email address. */
    @Transactional
    public void resendEmail(Integer payslipId) {
        Payslip p = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found: " + payslipId));
        Employee e = employeeRepository.findById(p.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + p.getEmployeeId()));
        if (e.getEmail() == null || e.getEmail().isBlank()) {
            throw new BadRequestException("This employee has no email address on file.");
        }

        byte[] pdf = payslipPdfService.generate(p);
        String monthLabel = LocalDate.of(p.getYear(), p.getMonth(), 1).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        eventPublisher.publishEvent(new PayslipEmailEvent(
                e.getEmail(),
                (e.getFirstName() + " " + e.getLastName()).trim(),
                e.getEmployeeCode(),
                monthLabel,
                p.getYear(),
                formatMoney(p.getNetPay()),
                pdf
        ));
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return String.format(Locale.ENGLISH, "%,.2f", amount);
    }

    private PayslipResponse toResponse(Payslip p) {
        Employee employee = employeeRepository.findById(p.getEmployeeId()).orElse(null);
        List<PayslipComponent> lines = payslipComponentRepository.findByPayslipIdOrderByDisplayOrder(p.getId());

        List<PayslipResponse.Line> earnings = lines.stream().filter(l -> "EARNING".equals(l.getComponentType()))
                .map(l -> new PayslipResponse.Line(l.getComponentName(), l.getAmount())).collect(Collectors.toList());
        List<PayslipResponse.Line> deductions = lines.stream().filter(l -> "DEDUCTION".equals(l.getComponentType()))
                .map(l -> new PayslipResponse.Line(l.getComponentName(), l.getAmount())).collect(Collectors.toList());
        List<PayslipResponse.Line> employerContributions = lines.stream().filter(l -> "EMPLOYER_CONTRIBUTION".equals(l.getComponentType()))
                .map(l -> new PayslipResponse.Line(l.getComponentName(), l.getAmount())).collect(Collectors.toList());

        return new PayslipResponse(
                p.getId(), p.getPayrollRunId(),
                p.getMonth(), p.getYear(),
                p.getEmployeeId(),
                employee != null ? employee.getEmployeeCode() : null,
                employee != null ? employee.getFirstName() + " " + employee.getLastName() : null,
                p.getWorkingDays(), p.getPaidDays(), p.getLopDays(),
                p.getGrossEarnings(), p.getTotalDeductions(), p.getNetPay(), p.getEmployerCost(),
                p.getPfEmployee(), p.getPfEmployer(), p.getPtAmount(), p.getTdsAmount(),
                p.getStatus(), earnings, deductions, employerContributions
        );
    }
}