package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.TaxSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaxSlabRepository extends JpaRepository<TaxSlab, Integer> {
    List<TaxSlab> findByFinancialYearAndRegimeOrderByFromAmountAsc(String financialYear, String regime);
}