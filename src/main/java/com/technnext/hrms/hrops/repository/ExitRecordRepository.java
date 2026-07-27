package com.technnext.hrms.hrops.repository;

import com.technnext.hrms.hrops.entity.ExitRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ExitRecordRepository extends JpaRepository<ExitRecord, Integer> {
    List<ExitRecord> findByEmployeeId(UUID employeeId);
}