package com.technnext.hrms.performance.repository;

import com.technnext.hrms.performance.entity.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Integer> {
    Optional<PerformanceReview> findByCycleIdAndEmployeeId(Integer cycleId, UUID employeeId);
    List<PerformanceReview> findByCycleIdOrderBySelfSubmittedAtAsc(Integer cycleId);
}