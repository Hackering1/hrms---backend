package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.PtSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PtSlabRepository extends JpaRepository<PtSlab, Integer> {
    List<PtSlab> findByStateAndIsActiveTrue(String state);
    List<PtSlab> findAllByOrderByStateAscMinSalaryAsc();
}