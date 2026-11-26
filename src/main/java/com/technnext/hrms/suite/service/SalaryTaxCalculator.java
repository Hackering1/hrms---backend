package com.technnext.hrms.suite.service;

import com.technnext.hrms.suite.entity.PayrollProfile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/** Salary-only payroll estimate; capital-gains and special-rate income are excluded. */
public final class SalaryTaxCalculator {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal CESS = new BigDecimal("0.04");

    private SalaryTaxCalculator() { }

    public static BigDecimal annualTax(PayrollProfile p, BigDecimal projectedSalary, LocalDate dateOfBirth,
                                       YearMonth period) {
        BigDecimal income = projectedSalary.add(n(p.getTaxOtherAnnualIncome()));
        boolean newRegime = !"OLD".equalsIgnoreCase(p.getTaxRegime());
        BigDecimal taxable = income.subtract(newRegime ? new BigDecimal("75000") : new BigDecimal("50000"));
        int taxYearEnd = period.getYear() + (period.getMonthValue() >= 4 ? 1 : 0);
        int age = dateOfBirth == null ? 0 : dateOfBirth.until(YearMonth.of(taxYearEnd, 3).atEndOfMonth()).getYears();
        boolean resident = Boolean.TRUE.equals(p.getTaxResident());

        if (!newRegime) {
            BigDecimal hraSalary = n(p.getBasic()).add(n(p.getDearnessAllowance())).multiply(new BigDecimal("12"));
            BigDecimal hra = n(p.getHra()).multiply(new BigDecimal("12"));
            BigDecimal rentExemption = n(p.getTaxRentPaidAnnual()).subtract(hraSalary.multiply(new BigDecimal("0.10"))).max(ZERO);
            BigDecimal salaryCap = hraSalary.multiply(Boolean.TRUE.equals(p.getTaxHraMetro())
                    ? new BigDecimal("0.50") : new BigDecimal("0.40"));
            BigDecimal hraExemption = hra.min(rentExemption).min(salaryCap);
            BigDecimal allowed = hraExemption
                    .add(n(p.getTaxLtaClaimAnnual()).min(n(p.getLta()).multiply(new BigDecimal("12"))))
                    .add(n(p.getTaxHomeLoanInterestAnnual()).min(new BigDecimal("200000")))
                    .add(n(p.getTax80cAnnual()).min(new BigDecimal("150000")))
                    .add(n(p.getTax80dAnnual()).min(age >= 60 ? new BigDecimal("50000") : new BigDecimal("25000")))
                    .add(n(p.getTaxNpsAnnual()).min(new BigDecimal("50000")));
            taxable = taxable.subtract(allowed);
        }
        taxable = taxable.max(ZERO).setScale(0, RoundingMode.DOWN);
        BigDecimal tax = newRegime ? newRegimeTax(taxable) : oldRegimeTax(taxable, resident, age);

        if (resident && newRegime && taxable.compareTo(new BigDecimal("1200000")) <= 0)
            tax = tax.subtract(new BigDecimal("60000")).max(ZERO);
        else if (resident && newRegime && taxable.compareTo(new BigDecimal("1200000")) > 0
                && taxable.compareTo(new BigDecimal("1275000")) <= 0)
            tax = tax.min(taxable.subtract(new BigDecimal("1200000")));
        if (resident && !newRegime && taxable.compareTo(new BigDecimal("500000")) <= 0)
            tax = tax.subtract(new BigDecimal("12500")).max(ZERO);

        BigDecimal surcharge = surcharge(taxable, tax, newRegime);
        return tax.add(surcharge).multiply(BigDecimal.ONE.add(CESS)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal newRegimeTax(BigDecimal income) {
        BigDecimal tax = ZERO;
        tax = tax.add(slice(income, 400000, 800000, 0.05));
        tax = tax.add(slice(income, 800000, 1200000, 0.10));
        tax = tax.add(slice(income, 1200000, 1600000, 0.15));
        tax = tax.add(slice(income, 1600000, 2000000, 0.20));
        tax = tax.add(slice(income, 2000000, 2400000, 0.25));
        tax = tax.add(slice(income, 2400000, Double.MAX_VALUE, 0.30));
        return tax;
    }

    private static BigDecimal oldRegimeTax(BigDecimal income, boolean resident, int age) {
        BigDecimal[] bands = resident && age >= 80
                ? new BigDecimal[]{new BigDecimal("500000"), new BigDecimal("1000000")}
                : resident && age >= 60
                ? new BigDecimal[]{new BigDecimal("300000"), new BigDecimal("500000"), new BigDecimal("1000000")}
                : new BigDecimal[]{new BigDecimal("250000"), new BigDecimal("500000"), new BigDecimal("1000000")};
        if (resident && age >= 80) return slice(income, 500000, 1000000, 0.20).add(slice(income, 1000000, Double.MAX_VALUE, 0.30));
        if (resident && age >= 60) return slice(income, 300000, 500000, 0.05)
                .add(slice(income, 500000, 1000000, 0.20)).add(slice(income, 1000000, Double.MAX_VALUE, 0.30));
        return slice(income, bands[0].doubleValue(), bands[1].doubleValue(), 0.05)
                .add(slice(income, 500000, 1000000, 0.20)).add(slice(income, 1000000, Double.MAX_VALUE, 0.30));
    }

    private static BigDecimal slice(BigDecimal income, double lower, double upper, double rate) {
        double amount = Math.max(0, Math.min(income.doubleValue(), upper) - lower);
        return BigDecimal.valueOf(amount * rate);
    }

    private static BigDecimal surcharge(BigDecimal income, BigDecimal tax, boolean newRegime) {
        if (income.compareTo(new BigDecimal("5000000")) <= 0) return ZERO;
        BigDecimal rate = income.compareTo(new BigDecimal("10000000")) <= 0 ? new BigDecimal("0.10")
                : income.compareTo(new BigDecimal("20000000")) <= 0 ? new BigDecimal("0.15")
                : income.compareTo(new BigDecimal("50000000")) <= 0 ? new BigDecimal("0.25")
                : new BigDecimal(newRegime ? "0.25" : "0.37");
        return tax.multiply(rate);
    }

    private static BigDecimal n(BigDecimal value) { return value == null ? ZERO : value; }
}

