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
import com.technnext.hrms.employee.repository.EmployeeRepository;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final SuiteRecordRepository repository;
    private final PayrollProfileRepository profiles;
    private final EmployeeRepository employees;

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
            BigDecimal fixedAllowance = profile.getGross().subtract(profile.getBasic())
                    .subtract(profile.getDearnessAllowance() == null ? BigDecimal.ZERO : profile.getDearnessAllowance())
                    .subtract(profile.getHra()).subtract(profile.getLta());
            if (fixedAllowance.signum() < 0) throw new BadRequestException("Basic, HRA and LTA cannot exceed gross monthly earnings.");
            if (repository.existsByModuleAndEmployeeIdAndPeriod("payroll", employeeId, request.period()))
                throw new BadRequestException("Payroll already exists for employee " + employeeId + " and period " + request.period() + ".");

            BigDecimal factor = BigDecimal.valueOf(input.paidDays()).divide(BigDecimal.valueOf(calendarDays), 8, RoundingMode.HALF_UP);
            BigDecimal basic = prorate(profile.getBasic(), factor);
            BigDecimal dearnessAllowance = prorate(profile.getDearnessAllowance() == null ? BigDecimal.ZERO : profile.getDearnessAllowance(), factor);
            BigDecimal hra = prorate(profile.getHra(), factor);
            BigDecimal lta = prorate(profile.getLta(), factor);
            BigDecimal allowance = prorate(fixedAllowance, factor);
            boolean bonusEnabled = Boolean.TRUE.equals(profile.getPerformanceBonusEnabled());
            BigDecimal performanceBonus = bonusEnabled
                    ? prorate(profile.getPerformanceBonus() == null ? BigDecimal.ZERO : profile.getPerformanceBonus(), factor)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal gross = basic.add(dearnessAllowance).add(hra).add(lta).add(allowance).add(performanceBonus)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal employeePf = calculatePf(profile, basic, factor);
            BigDecimal employeeEsi = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal professionalTax = prorate(profile.getProfessionalTax(), factor);
            BigDecimal tds = calculateTds(profile, employeeId, request.period(), gross, performanceBonus, calendarDays);
            BigDecimal net = gross.subtract(employeePf).subtract(employeeEsi).subtract(professionalTax).subtract(tds).setScale(2, RoundingMode.HALF_UP);
            if (net.signum() < 0) throw new BadRequestException("Deductions cannot exceed gross salary.");
            BigDecimal employerPf = employeePf;
            BigDecimal employerEsi = percent(gross, profile.getEmployerEsiRate());
            BigDecimal gratuity = prorate(profile.getGratuity(), factor);
            BigDecimal gmi = profile.getGroupMedicalInsurance() == null ? new BigDecimal("1250.00") : profile.getGroupMedicalInsurance();
            BigDecimal gmiGst = percent(gmi, new BigDecimal("18.00"));
            BigDecimal employerCost = gross.add(employerPf).add(employerEsi).add(gratuity).add(gmi).add(gmiGst).setScale(2, RoundingMode.HALF_UP);

            var breakdown = new PayrollBreakdown(profile.getGross(), basic, dearnessAllowance, hra, lta, allowance, gross,
                    employeePf, employeeEsi, professionalTax, tds, net, employerPf, employerEsi,
                    gratuity, employerCost, gmi, gmiGst, bonusEnabled, performanceBonus,
                    profile.getPfContributionMode(), profile.getTaxRegime(), profile.getPfRate(), profile.getEsiRate(),
                    profile.getEmployerPfRate(), profile.getEmployerEsiRate(), input.paidDays(),
                    input.lopDays(), calendarDays, profile.getPolicyVersion());
            String details;
            try { details = OBJECT_MAPPER.writeValueAsString(breakdown); }
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

    private BigDecimal calculatePf(PayrollProfile profile, BigDecimal proratedBasic, BigDecimal factor) {
        if (profile.getBasic().compareTo(new BigDecimal("25000")) < 0
                || !"FLAT_3000".equals(profile.getPfContributionMode()))
            return percent(proratedBasic, new BigDecimal("12.00"));
        return new BigDecimal("3000.00").multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTds(PayrollProfile profile, UUID employeeId, String period,
                                    BigDecimal currentGross, BigDecimal currentBonus, int calendarDays) {
        YearMonth month = YearMonth.parse(period);
        int monthInTaxYear = (month.getMonthValue() + 8) % 12 + 1;
        int remainingMonths = 13 - monthInTaxYear;
        String taxYearStart = (month.getMonthValue() >= 4 ? month.getYear() : month.getYear() - 1) + "-04";
        BigDecimal pastGross = BigDecimal.ZERO;
        BigDecimal pastTds = BigDecimal.ZERO;
        for (SuiteRecord prior : repository.findByModuleAndEmployeeIdOrderByCreatedAtDesc("payroll", employeeId)) {
            if (prior.getPeriod() == null
                    || prior.getPeriod().compareTo(taxYearStart) < 0 || prior.getPeriod().compareTo(period) >= 0) continue;
            try {
                var details = OBJECT_MAPPER.readTree(prior.getDetails() == null ? "{}" : prior.getDetails());
                pastGross = pastGross.add(details.path("gross").decimalValue());
                pastTds = pastTds.add(details.path("tds").decimalValue());
            } catch (JsonProcessingException ignored) {
                // Historical malformed details are omitted from the projection and remain visible for review.
            }
        }
        BigDecimal fullMonthSalary = profile.getGross().add(Boolean.TRUE.equals(profile.getPerformanceBonusEnabled())
                ? (profile.getPerformanceBonus() == null ? BigDecimal.ZERO : profile.getPerformanceBonus()) : BigDecimal.ZERO);
        int futureMonths = 0;
        var employee = employees.findById(employeeId).orElse(null);
        YearMonth joining = employee == null || employee.getDateOfJoining() == null
                ? null : YearMonth.from(employee.getDateOfJoining());
        for (int offset = 1; offset < remainingMonths; offset++)
            if (joining == null || !month.plusMonths(offset).isBefore(joining)) futureMonths++;
        BigDecimal projectedSalary = pastGross.add(currentGross)
                .add(fullMonthSalary.multiply(BigDecimal.valueOf(futureMonths)))
                .add(profile.getTaxPreviousEmployerIncome() == null ? BigDecimal.ZERO : profile.getTaxPreviousEmployerIncome());
        BigDecimal annualTax = SalaryTaxCalculator.annualTax(profile, projectedSalary,
                employee == null ? null : employee.getDateOfBirth(), month);
        BigDecimal outstanding = annualTax
                .subtract(profile.getTaxPreviousEmployerTds() == null ? BigDecimal.ZERO : profile.getTaxPreviousEmployerTds())
                .subtract(pastTds).max(BigDecimal.ZERO);
        return outstanding.divide(BigDecimal.valueOf(Math.max(1, remainingMonths)), 2, RoundingMode.CEILING);
    }

    public record PayrollBreakdown(BigDecimal monthlyGross, BigDecimal basic, BigDecimal dearnessAllowance, BigDecimal hra, BigDecimal lta,
            BigDecimal fixedAllowance, BigDecimal gross, BigDecimal employeePf, BigDecimal employeeEsi,
            BigDecimal professionalTax, BigDecimal tds, BigDecimal netPay, BigDecimal employerPf,
            BigDecimal employerEsi, BigDecimal gratuity, BigDecimal employerCost,
            BigDecimal groupMedicalInsurance, BigDecimal groupMedicalInsuranceGst,
            boolean performanceBonusEnabled, BigDecimal performanceBonus,
            String pfContributionMode, String taxRegime,
            BigDecimal employeePfRate, BigDecimal employeeEsiRate, BigDecimal employerPfRate,
            BigDecimal employerEsiRate, int paidDays, int lopDays, int calendarDays, String policyVersion) { }
}

