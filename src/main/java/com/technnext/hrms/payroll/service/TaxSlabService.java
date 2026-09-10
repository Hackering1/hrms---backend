package com.technnext.hrms.payroll.service;

import com.technnext.hrms.payroll.entity.TaxSettings;
import com.technnext.hrms.payroll.entity.TaxSlab;
import com.technnext.hrms.payroll.repository.TaxSettingsRepository;
import com.technnext.hrms.payroll.repository.TaxSlabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaxSlabService {

    private final TaxSlabRepository slabRepository;
    private final TaxSettingsRepository settingsRepository;

    @Transactional(readOnly = true)
    public List<TaxSlab> getSlabs(String financialYear, String regime) {
        return slabRepository.findByFinancialYearAndRegimeOrderByFromAmountAsc(financialYear, regime == null ? "NEW" : regime);
    }

    @Transactional(readOnly = true)
    public TaxSettings getSettings(String financialYear) {
        return settingsRepository.findByFinancialYear(financialYear)
                .orElseGet(() -> TaxSettings.builder()
                        .financialYear(financialYear)
                        .standardDeduction(new java.math.BigDecimal("75000"))
                        .rebate87aIncomeLimit(new java.math.BigDecimal("1200000"))
                        .rebate87aAmount(new java.math.BigDecimal("60000"))
                        .cessPercent(new java.math.BigDecimal("4.00"))
                        .build());
    }

    /** Replaces every slab row for a given FY+regime with a new set — used when a new Budget changes the slabs. */
    @Transactional
    public List<TaxSlab> replaceSlabs(String financialYear, String regime, List<TaxSlab> newSlabs) {
        List<TaxSlab> existing = slabRepository.findByFinancialYearAndRegimeOrderByFromAmountAsc(financialYear, regime);
        slabRepository.deleteAll(existing);
        newSlabs.forEach(s -> { s.setId(null); s.setFinancialYear(financialYear); s.setRegime(regime); });
        return slabRepository.saveAll(newSlabs);
    }

    @Transactional
    public TaxSettings upsertSettings(TaxSettings body) {
        TaxSettings existing = settingsRepository.findByFinancialYear(body.getFinancialYear()).orElse(null);
        if (existing == null) {
            body.setId(null);
            return settingsRepository.save(body);
        }
        existing.setStandardDeduction(body.getStandardDeduction());
        existing.setRebate87aIncomeLimit(body.getRebate87aIncomeLimit());
        existing.setRebate87aAmount(body.getRebate87aAmount());
        existing.setCessPercent(body.getCessPercent());
        return settingsRepository.save(existing);
    }
}