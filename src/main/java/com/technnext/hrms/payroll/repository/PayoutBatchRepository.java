package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.PayoutBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PayoutBatchRepository extends JpaRepository<PayoutBatch, Integer> {
    Optional<PayoutBatch> findByPayrollRunId(Integer payrollRunId);
    Optional<PayoutBatch> findByBatchTransferId(String batchTransferId);
}