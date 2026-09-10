package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.TaxSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TaxSettingsRepository extends JpaRepository<TaxSettings, Integer> {
    Optional<TaxSettings> findByFinancialYear(String financialYear);
}