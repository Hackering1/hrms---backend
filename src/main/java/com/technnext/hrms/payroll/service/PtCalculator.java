package com.technnext.hrms.payroll.service;

import com.technnext.hrms.payroll.entity.PtSlab;
import com.technnext.hrms.payroll.repository.PtSlabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * State-wise Professional Tax. Looks up the slab matching the employee's work
 * state + gender + gross monthly salary, and applies the February bump where
 * a state (Karnataka, Maharashtra) uses one to hit the Rs.2,500 annual cap.
 *
 * Slabs are DATA (pt_slabs table / Payroll > Statutory Settings screen), not
 * hardcoded here, because state governments revise these periodically —
 * Finance should review them each financial year, not wait for a code change.
 */
@Service
@RequiredArgsConstructor
public class PtCalculator {

    private final PtSlabRepository ptSlabRepository;

    /**
     * @param state           employee's work-location state (from their Branch), e.g. "Karnataka"
     * @param gender          MALE | FEMALE | null (treated as ALL-only match if null/unknown)
     * @param grossMonthly    gross monthly salary used to find the applicable slab
     * @param month           1-12; February (2) triggers the february_amount override where set
     * @param ptApplicable    per-employee override (e.g. exempt for a specific reason)
     */
    public BigDecimal calculate(String state, String gender, BigDecimal grossMonthly, int month, boolean ptApplicable) {
        if (!ptApplicable || state == null || grossMonthly == null || grossMonthly.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        List<PtSlab> slabs = ptSlabRepository.findByStateAndIsActiveTrue(state);
        if (slabs.isEmpty()) {
            // No PT configured for this state at all — treat as not applicable rather than guessing.
            return BigDecimal.ZERO;
        }

        String g = gender == null ? "ALL" : gender.toUpperCase();
        PtSlab match = slabs.stream()
                .filter(s -> s.getGender().equals("ALL") || s.getGender().equals(g))
                .filter(s -> grossMonthly.compareTo(s.getMinSalary()) >= 0)
                .filter(s -> s.getMaxSalary() == null || grossMonthly.compareTo(s.getMaxSalary()) <= 0)
                // Prefer a gender-specific slab over an ALL slab if both match the salary band.
                .sorted((a, b) -> a.getGender().equals("ALL") ? 1 : -1)
                .findFirst()
                .orElse(null);

        if (match == null) return BigDecimal.ZERO;

        if (month == 2 && match.getFebruaryAmount() != null) {
            return match.getFebruaryAmount();
        }
        return match.getMonthlyAmount();
    }
}