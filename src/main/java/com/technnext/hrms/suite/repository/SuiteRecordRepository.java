package com.technnext.hrms.suite.repository;

import com.technnext.hrms.suite.entity.SuiteRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SuiteRecordRepository extends JpaRepository<SuiteRecord, UUID> {
    List<SuiteRecord> findByModuleOrderByCreatedAtDesc(String module);
    List<SuiteRecord> findByModuleAndEmployeeIdInOrderByCreatedAtDesc(String module, List<UUID> employeeIds);
    List<SuiteRecord> findByModuleAndEmployeeIdOrderByCreatedAtDesc(String module, UUID employeeId);
    Optional<SuiteRecord> findByIdAndModule(UUID id, String module);
    boolean existsByModuleAndEmployeeIdAndPeriod(String module, UUID employeeId, String period);
    Optional<SuiteRecord> findByModuleAndEmployeeIdAndPeriod(String module, UUID employeeId, String period);
    @Modifying
    @Transactional
    @Query("update SuiteRecord r set r.status = 'EMAIL_SENDING' where r.id = :id and r.module = 'payroll' and r.status in ('CALCULATED', 'EMAIL_FAILED')")
    int claimPayrollEmail(@Param("id") UUID id);
    boolean existsByModuleAndEmployeeIdAndStatus(String module, UUID employeeId, String status);
}
