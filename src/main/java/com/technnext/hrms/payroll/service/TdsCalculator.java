package com.technnext.hrms.payroll.service;

import com.technnext.hrms.payroll.entity.TaxSettings;
import com.technnext.hrms.payroll.entity.TaxSlab;
import com.technnext.hrms.payroll.repository.TaxSettingsRepository;
import com.technnext.hrms.payroll.repository.TaxSlabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Estimates monthly TDS under Section 192, NEW tax regime only (the default
 * regime since Budget 2025/2026 — see PayrollCalculationService for what's
 * out of scope).
 *
 * SCOPE / KNOWN LIMITATIONS (deliberate, to keep this correct rather than
 * fake-precise):
 *  - OLD regime (HRA exemption, Section 80C/80D investment declarations,
 *    home-loan interest, etc.) is NOT computed. An employee on the OLD
 *    regime must have tdsOverrideMonthly set on their EmployeeSalary —
 *    the payroll run will use that instead of calling this calculator.
 *  - Surcharge (income > Rs.50L) is NOT applied — flag any such employee
 *    for manual review/override rather than silently under/over-deducting.
 *  - Other income (rental, capital gains, previous employer in the same
 *    FY) is NOT accounted for — only this company's salary.
 *  - Employer NPS contribution (80CCD(2), still allowed in the new regime)
 *    is NOT modelled as a separate deduction in this version.
 *
 * METHOD: projects the employee's ANNUAL taxable salary from their current
 * monthly taxable gross (assumes a stable monthly salary for the rest of the
 * FY — the standard simplified approach), computes the full-year tax
 * liability once, then spreads (liability - already withheld this FY) evenly
 * across the remaining months of the financial year. This self-corrects if
 * a mid-year raise or bonus changes the monthly gross — next month's run
 * recomputes off the new monthly figure and the updated YTD-withheld total.
 */
@Service
@RequiredArgsConstructor
public class TdsCalculator {

    private final TaxSlabRepository taxSlabRepository;
    private final TaxSettingsRepository taxSettingsRepository;

    /**
     * @param monthlyTaxableGross the employee's current monthly taxable earnings (prorated for LOP)
     * @param tdsAlreadyPaidThisFy sum of TDS withheld in this financial year prior to this run
     * @param calendarMonth 1-12 (the payroll run's month)
     * @param calendarYear  the payroll run's year
     */
    public BigDecimal calculateMonthlyTds(BigDecimal monthlyTaxableGross, BigDecimal tdsAlreadyPaidThisFy, int calendarMonth, int calendarYear) {
        if (monthlyTaxableGross == null || monthlyTaxableGross.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        String financialYear = financialYearFor(calendarMonth, calendarYear);
        List<TaxSlab> slabs = taxSlabRepository.findByFinancialYearAndRegimeOrderByFromAmountAsc(financialYear, "NEW");
        TaxSettings settings = taxSettingsRepository.findByFinancialYear(financialYear)
                .orElseGet(() -> TaxSettings.builder()
                        .financialYear(financialYear)
                        .standardDeduction(new BigDecimal("75000"))
                        .rebate87aIncomeLimit(new BigDecimal("1200000"))
                        .rebate87aAmount(new BigDecimal("60000"))
                        .cessPercent(new BigDecimal("4.00"))
                        .build());

        if (slabs.isEmpty()) {
            // No slab data for this FY yet (e.g. new financial year, Budget update not
            // entered) — fail safe to zero rather than guess at tax rates, and let the
            // payroll run flag it for HR to add the slabs before approving.
            return BigDecimal.ZERO;
        }

        BigDecimal annualProjectedGross = monthlyTaxableGross.multiply(BigDecimal.valueOf(12));
        BigDecimal taxableIncome = annualProjectedGross.subtract(settings.getStandardDeduction()).max(BigDecimal.ZERO);

        BigDecimal annualTaxBeforeCess = computeSlabTax(taxableIncome, slabs);

        if (taxableIncome.compareTo(settings.getRebate87aIncomeLimit()) <= 0) {
            BigDecimal rebate = annualTaxBeforeCess.min(settings.getRebate87aAmount());
            annualTaxBeforeCess = annualTaxBeforeCess.subtract(rebate).max(BigDecimal.ZERO);
        }

        BigDecimal cess = annualTaxBeforeCess
                .multiply(settings.getCessPercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal annualTaxLiability = annualTaxBeforeCess.add(cess);

        int remainingMonths = remainingMonthsInFinancialYear(calendarMonth);
        BigDecimal alreadyPaid = tdsAlreadyPaidThisFy == null ? BigDecimal.ZERO : tdsAlreadyPaidThisFy;
        BigDecimal remainingLiability = annualTaxLiability.subtract(alreadyPaid).max(BigDecimal.ZERO);

        return remainingLiability.divide(BigDecimal.valueOf(remainingMonths), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeSlabTax(BigDecimal taxableIncome, List<TaxSlab> slabs) {
        BigDecimal tax = BigDecimal.ZERO;
        for (TaxSlab slab : slabs) {
            if (taxableIncome.compareTo(slab.getFromAmount()) <= 0) continue;
            BigDecimal bandTop = slab.getToAmount() == null ? taxableIncome : slab.getToAmount().min(taxableIncome);
            BigDecimal bandAmount = bandTop.subtract(slab.getFromAmount());
            if (bandAmount.signum() <= 0) continue;
            tax = tax.add(bandAmount.multiply(slab.getRatePercent()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    /** Indian FY runs April -> March. Jan/Feb/Mar belong to the FY that started the previous calendar year. */
    String financialYearFor(int calendarMonth, int calendarYear) {
        int startYear = calendarMonth >= 4 ? calendarYear : calendarYear - 1;
        int endYearShort = (startYear + 1) % 100;
        return startYear + "-" + String.format("%02d", endYearShort);
    }

    /** Months left in the financial year, INCLUDING the current one (April = 12 remaining, March = 1 remaining). */
    int remainingMonthsInFinancialYear(int calendarMonth) {
        int fyMonthIndex = calendarMonth >= 4 ? (calendarMonth - 3) : (calendarMonth + 9); // Apr=1 ... Mar=12
        return 13 - fyMonthIndex;
    }
}