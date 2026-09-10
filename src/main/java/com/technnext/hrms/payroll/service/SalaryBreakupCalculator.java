package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.payroll.entity.SalaryStructureComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns an annual CTC + a Salary Structure's component list into a concrete
 * monthly/annual amount per component (Zoho-style: Basic -> HRA off Basic ->
 * flat items -> Special Allowance absorbs whatever CTC is left over).
 *
 * Order of resolution (matters — later steps depend on earlier ones):
 *   1. PERCENT_OF_CTC   (e.g. Basic = 50% of annual CTC)
 *   2. PERCENT_OF_BASIC (e.g. HRA = 40% of Basic)
 *   3. FLAT              (fixed monthly amount, e.g. Conveyance)
 *   4. REMAINDER         (whatever's left of CTC after the above — at most one
 *                         REMAINDER component per structure, enforced below)
 *
 * Statutory components (PF/PT/TDS, is_statutory = true) are deliberately
 * EXCLUDED from this breakup — they're computed separately per payroll run
 * by PfCalculator / PtCalculator / TdsCalculator, since they depend on
 * month-specific facts (LOP days, February PT bump, YTD tax paid) that a
 * static CTC breakup can't know.
 */
@Service
@RequiredArgsConstructor
public class SalaryBreakupCalculator {

    public record ComponentAmount(Integer salaryComponentId, BigDecimal monthlyAmount, BigDecimal annualAmount) {}

    /**
     * @param components   the structure's non-statutory EARNING components (deductions/employer
     *                     contributions are computed elsewhere, not part of the CTC split)
     * @param basicComponentId id of the component whose calculationType is PERCENT_OF_CTC and
     *                     which PERCENT_OF_BASIC components should be based on (normally "Basic")
     */
    public List<ComponentAmount> computeBreakup(BigDecimal annualCtc, List<SalaryStructureComponent> components, Integer basicComponentId) {
        if (annualCtc == null || annualCtc.signum() <= 0) {
            throw new BadRequestException("Annual CTC must be a positive amount.");
        }

        BigDecimal monthlyCtc = annualCtc.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        Map<Integer, BigDecimal> monthlyById = new java.util.HashMap<>();
        BigDecimal remainderTotal = monthlyCtc; // what's left of the monthly CTC as we allocate

        // 1) PERCENT_OF_CTC (e.g. Basic)
        for (SalaryStructureComponent c : components) {
            if (!"PERCENT_OF_CTC".equals(c.getCalculationType())) continue;
            requirePercentage(c);
            BigDecimal amt = monthlyCtc.multiply(c.getPercentage()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            monthlyById.put(c.getSalaryComponentId(), amt);
            remainderTotal = remainderTotal.subtract(amt);
        }

        BigDecimal basicMonthly = monthlyById.getOrDefault(basicComponentId, BigDecimal.ZERO);

        // 2) PERCENT_OF_BASIC (e.g. HRA)
        for (SalaryStructureComponent c : components) {
            if (!"PERCENT_OF_BASIC".equals(c.getCalculationType())) continue;
            requirePercentage(c);
            if (basicMonthly.signum() == 0) {
                throw new BadRequestException("A PERCENT_OF_BASIC component requires a Basic (PERCENT_OF_CTC) component in the same structure.");
            }
            BigDecimal amt = basicMonthly.multiply(c.getPercentage()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            monthlyById.put(c.getSalaryComponentId(), amt);
            remainderTotal = remainderTotal.subtract(amt);
        }

        // 3) FLAT
        for (SalaryStructureComponent c : components) {
            if (!"FLAT".equals(c.getCalculationType())) continue;
            BigDecimal amt = c.getFlatAmount() != null ? c.getFlatAmount() : BigDecimal.ZERO;
            monthlyById.put(c.getSalaryComponentId(), amt);
            remainderTotal = remainderTotal.subtract(amt);
        }

        // 4) REMAINDER (Special Allowance) — at most one, absorbs whatever's left.
        List<SalaryStructureComponent> remainderComponents = components.stream()
                .filter(c -> "REMAINDER".equals(c.getCalculationType()))
                .toList();
        if (remainderComponents.size() > 1) {
            throw new BadRequestException("A salary structure can have at most one REMAINDER component.");
        }
        if (remainderComponents.size() == 1) {
            BigDecimal amt = remainderTotal.max(BigDecimal.ZERO); // never go negative; CTC over-allocation is a config error, not a negative allowance
            monthlyById.put(remainderComponents.get(0).getSalaryComponentId(), amt);
        } else if (remainderTotal.compareTo(BigDecimal.ZERO) != 0) {
            throw new BadRequestException(
                    "The structure's fixed/percentage components don't add up to the full CTC and there's no REMAINDER "
                    + "component to absorb the difference (Rs." + remainderTotal + " left over). Add a REMAINDER component or adjust the percentages.");
        }

        return monthlyById.entrySet().stream()
                .map(e -> new ComponentAmount(e.getKey(), e.getValue(), e.getValue().multiply(BigDecimal.valueOf(12))))
                .collect(Collectors.toList());
    }

    private void requirePercentage(SalaryStructureComponent c) {
        if (c.getPercentage() == null) {
            throw new BadRequestException("Component id " + c.getSalaryComponentId() + " uses a percentage calculation type but has no percentage set.");
        }
    }
}