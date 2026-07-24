package com.technnext.hrms.hrops.repository;

import com.technnext.hrms.hrops.entity.ConfirmationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ConfirmationRecordRepository extends JpaRepository<ConfirmationRecord, Integer> {
    List<ConfirmationRecord> findByEmployeeId(UUID employeeId);
}