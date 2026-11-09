package com.technnext.hrms.suite.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.suite.dto.PayrollRunEmployeeInput;
import com.technnext.hrms.suite.dto.PayrollRunRequest;
import com.technnext.hrms.suite.entity.PayrollProfile;
import com.technnext.hrms.suite.entity.SuiteRecord;
import com.technnext.hrms.suite.repository.PayrollProfileRepository;
import com.technnext.hrms.suite.repository.SuiteRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollRunService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final SuiteRecordRepository repository;
    private final PayrollProfileRepository profiles;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<SuiteRecord> calculateAndGenerate(PayrollRunRequest request, UUID actorId) {
        List<SuiteRecord> output = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        List<UUID> employeeIds = request.employees().stream().map(PayrollRunEmployeeInput::employeeId).toList();
        Map<UUID, PayrollProfile> profileByEmployee = profiles.findByEmployeeIdIn(employeeIds).stream()
                .collect(Collectors.toMap(PayrollProfile::getEmployeeId, profile -> profile));
        int calendarDays = YearMonth.parse(request.period()).lengthOfMonth();

        for (PayrollRunEmployeeInput input : request.employees()) {
            UUID employeeId = input.employeeId();
            if (!seen.add(employeeId)) throw new BadRequestException("An employee can appear only once in a payroll run.");
            if (input.paidDays() > calendarDays || input.paidDays() + input.lopDays() > calendarDays)
                throw new BadRequestException("Paid days plus LOP days cannot exceed the calendar days in " + request.period() + ".");
            PayrollProfile profile = profileByEmployee.get(employeeId);
            if (profile == null) throw new BadRequestException("Save a payroll profile before running payroll for employee " + employeeId + ".");
            BigDecimal fixedAllowance = profile.getGross().subtract(profile.getBasic()).subtract(profile.getHra()).subtract(profile.getLta());
            if (fixedAllowance.signum() < 0) throw new BadRequestException("Basic, HRA and LTA cannot exceed gross monthly earnings.");
            if (repository.existsByModuleAndEmployeeIdAndPeriod("payroll", employeeId, request.period()))
                throw new BadRequestException("Payroll already exists for employee " + employeeId + " and period " + request.period() + ".");

            BigDecimal factor = BigDecimal.valueOf(input.paidDays()).divide(BigDecimal.valueOf(calendarDays), 8, RoundingMode.HALF_UP);
            BigDecimal basic = prorate(profile.getBasic(), factor);
            BigDecimal hra = prorate(profile.getHra(), factor);
            BigDecimal lta = prorate(profile.getLta(), factor);
            BigDecimal allowance = prorate(fixedAllowance, factor);
            BigDecimal gross = basic.add(hra).add(lta).add(allowance).setScale(2, RoundingMode.HALF_UP);
            BigDecimal employeePf = percent(basic, profile.getPfRate());
            BigDecimal employeeEsi = percent(gross, profile.getEsiRate());
            BigDecimal professionalTax = prorate(profile.getProfessionalTax(), factor);
            BigDecimal tds = prorate(profile.getTds(), factor);
            BigDecimal net = gross.subtract(employeePf).subtract(employeeEsi).subtract(professionalTax).subtract(tds).setScale(2, RoundingMode.HALF_UP);
            if (net.signum() < 0) throw new BadRequestException("Deductions cannot exceed gross salary.");
            BigDecimal employerPf = percent(basic, profile.getEmployerPfRate());
            BigDecimal employerEsi = percent(gross, profile.getEmployerEsiRate());
            BigDecimal gratuity = prorate(profile.getGratuity(), factor);
            BigDecimal employerCost = gross.add(employerPf).add(employerEsi).add(gratuity).setScale(2, RoundingMode.HALF_UP);

            var breakdown = new PayrollBreakdown(profile.getGross(), basic, hra, lta, allowance, gross,
                    employeePf, employeeEsi, professionalTax, tds, net, employerPf, employerEsi,
                    gratuity, employerCost, profile.getPfRate(), profile.getEsiRate(),
                    profile.getEmployerPfRate(), profile.getEmployerEsiRate(), input.paidDays(),
                    input.lopDays(), calendarDays, profile.getPolicyVersion());
            String details;
            try { details = objectMapper.writeValueAsString(breakdown); }
            catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize payroll breakdown", e); }

            SuiteRecord payroll = SuiteRecord.builder().module("payroll").title("Payroll " + request.period())
                    .employeeId(employeeId).status("CALCULATED").period(request.period()).amount(net)
                    .details(details).createdBy(actorId).build();
            output.add(repository.save(payroll));
            SuiteRecord payslip = SuiteRecord.builder().module("payslips").title("Payslip " + request.period())
                    .employeeId(employeeId).status("PENDING_APPROVAL").period(request.period()).amount(net)
                    .details(details).createdBy(actorId).build();
            repository.save(payslip);
        }
        return output;
    }

    private BigDecimal prorate(BigDecimal value, BigDecimal factor) {
        return value.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public record PayrollBreakdown(BigDecimal monthlyGross, BigDecimal basic, BigDecimal hra, BigDecimal lta,
            BigDecimal fixedAllowance, BigDecimal gross, BigDecimal employeePf, BigDecimal employeeEsi,
            BigDecimal professionalTax, BigDecimal tds, BigDecimal netPay, BigDecimal employerPf,
            BigDecimal employerEsi, BigDecimal gratuity, BigDecimal employerCost,
            BigDecimal employeePfRate, BigDecimal employeeEsiRate, BigDecimal employerPfRate,
            BigDecimal employerEsiRate, int paidDays, int lopDays, int calendarDays, String policyVersion) { }
}
