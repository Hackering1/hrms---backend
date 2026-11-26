package com.technnext.hrms.suite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.technnext.hrms.suite.entity.PayrollProfile;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class SalaryTaxCalculatorTest {
    @Test
    void newRegimeTaxIsZeroAtTwelveLakhTaxableIncomeForResident() {
        PayrollProfile profile = PayrollProfile.builder()
                .taxRegime("NEW").taxResident(true).taxOtherAnnualIncome(BigDecimal.ZERO).build();
        assertEquals(0, SalaryTaxCalculator.annualTax(profile, new BigDecimal("1275000"), null,
                YearMonth.of(2026, 10)).compareTo(BigDecimal.ZERO));
    }

    @Test
    void newRegimeTaxIsChargedAboveRebateThreshold() {
        PayrollProfile profile = PayrollProfile.builder()
                .taxRegime("NEW").taxResident(true).taxOtherAnnualIncome(BigDecimal.ZERO).build();
        assertEquals(0, SalaryTaxCalculator.annualTax(profile, new BigDecimal("1300000"), null,
                YearMonth.of(2026, 10)).compareTo(new BigDecimal("26000.00")));
    }
}

