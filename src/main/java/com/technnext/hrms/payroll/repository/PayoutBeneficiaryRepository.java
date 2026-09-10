package com.technnext.hrms.payroll.repository;

import com.technnext.hrms.payroll.entity.PayoutBeneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PayoutBeneficiaryRepository extends JpaRepository<PayoutBeneficiary, Integer> {
    Optional<PayoutBeneficiary> findByEmployeeId(UUID employeeId);
}