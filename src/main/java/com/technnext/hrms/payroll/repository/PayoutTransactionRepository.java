package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.PayoutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PayoutTransactionRepository extends JpaRepository<PayoutTransaction, Integer> {
    List<PayoutTransaction> findByPayoutBatchId(Integer payoutBatchId);
    Optional<PayoutTransaction> findByTransferId(String transferId);
    Optional<PayoutTransaction> findByCfTransferId(String cfTransferId);
}