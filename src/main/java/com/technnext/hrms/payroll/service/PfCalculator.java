package com.technnext.hrms.payroll.service;

import com.technnext.hrms.payroll.entity.PfSettings;
import com.technnext.hrms.payroll.repository.PfSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Employee Provident Fund (EPFO). Standard rule confirmed with HR: the PF WAGE
 * is capped at the statutory ceiling (currently Rs.15,000/month) even when
 * actual Basic is higher — i.e. employee PF = 12% x min(Basic, ceiling), which
 * tops out at Rs.1,800/month, not an uncapped 12% of the real Basic.
 */
@Service
@RequiredArgsConstructor
public class PfCalculator {

    private final PfSettingsRepository pfSettingsRepository;

    public record PfResult(BigDecimal employeeContribution, BigDecimal employerContribution, BigDecimal epsContribution) {}

    public PfSettings currentSettings() {
        List<PfSettings> rows = pfSettingsRepository.findAllByOrderByEffectiveFromDesc();
        if (rows.isEmpty()) {
            // Safe fallback matching the migration's seed values, so payroll doesn't
            // hard-fail if the settings row was ever deleted.
            return PfSettings.builder()
                    .isEnabled(true)
                    .employeeRatePercent(new BigDecimal("12.00"))
                    .employerRatePercent(new BigDecimal("12.00"))
                    .epsRatePercent(new BigDecimal("8.33"))
                    .wageCeiling(new BigDecimal("15000.00"))
                    .epsWageCeiling(new BigDecimal("15000.00"))
                    .build();
        }
        return rows.get(0);
    }

    /** @param monthlyBasic the employee's monthly Basic pay (already prorated for LOP, if applicable) */
    public PfResult calculate(BigDecimal monthlyBasic, boolean pfApplicable) {
        PfSettings s = currentSettings();
        if (!pfApplicable || !Boolean.TRUE.equals(s.getIsEnabled()) || monthlyBasic == null || monthlyBasic.signum() <= 0) {
            return new PfResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal pfWage = monthlyBasic.min(s.getWageCeiling());
        BigDecimal epsWage = monthlyBasic.min(s.getEpsWageCeiling());

        BigDecimal employeeContribution = pfWage
                .multiply(s.getEmployeeRatePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal epsContribution = epsWage
                .multiply(s.getEpsRatePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal employerTotal = pfWage
                .multiply(s.getEmployerRatePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        // Employer's 12% splits into EPS (8.33%, capped) + the remainder into EPF proper.
        BigDecimal employerEpfShare = employerTotal.subtract(epsContribution).max(BigDecimal.ZERO);

        return new PfResult(employeeContribution, employerEpfShare.add(epsContribution), epsContribution);
    }
}