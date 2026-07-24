package com.technnext.hrms.hrops.repository;

import com.technnext.hrms.hrops.entity.ProbationTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProbationTrackingRepository extends JpaRepository<ProbationTracking, Integer> {
    List<ProbationTracking> findByEmployeeId(UUID employeeId);
    List<ProbationTracking> findByStatus(String status);
}