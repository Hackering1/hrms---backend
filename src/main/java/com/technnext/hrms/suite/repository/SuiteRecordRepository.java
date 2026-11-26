package com.technnext.hrms.suite.repository;

import com.technnext.hrms.suite.entity.SuiteRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

public interface SuiteRecordRepository extends JpaRepository<SuiteRecord, UUID> {
    List<SuiteRecord> findByModuleOrderByCreatedAtDesc(String module);
    List<SuiteRecord> findByModuleAndArchivedAtIsNullOrderByCreatedAtDesc(String module);
    List<SuiteRecord> findByModuleAndEmployeeIdInOrderByCreatedAtDesc(String module, List<UUID> employeeIds);
    List<SuiteRecord> findByModuleAndEmployeeIdOrderByCreatedAtDesc(String module, UUID employeeId);
    List<SuiteRecord> findByModuleAndEmployeeIdAndStartDateBetweenOrderByCreatedAtAsc(
            String module, UUID employeeId, LocalDate startDate, LocalDate endDate);
    Optional<SuiteRecord> findByIdAndModule(UUID id, String module);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SuiteRecord r where r.id = :id and r.module = :module")
    Optional<SuiteRecord> lockByIdAndModule(@Param("id") UUID id, @Param("module") String module);
    boolean existsByModuleAndEmployeeIdAndPeriod(String module, UUID employeeId, String period);
    Optional<SuiteRecord> findByModuleAndEmployeeIdAndPeriod(String module, UUID employeeId, String period);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SuiteRecord r where r.module = :module and r.employeeId = :employeeId and r.period = :period")
    Optional<SuiteRecord> lockByModuleAndEmployeeIdAndPeriod(@Param("module") String module,
                                                              @Param("employeeId") UUID employeeId,
                                                              @Param("period") String period);
    @Modifying
    @Transactional
    @Query("update SuiteRecord r set r.status = 'EMAIL_SENDING' where r.id = :id and r.module = 'payroll' and r.archivedAt is null and r.status in ('CALCULATED', 'EMAIL_FAILED', 'APPROVED')")
    int claimPayrollEmail(@Param("id") UUID id);
    boolean existsByModuleAndEmployeeIdAndStatus(String module, UUID employeeId, String status);
}

