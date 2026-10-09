package com.technnext.hrms.performance.repository;

import com.technnext.hrms.performance.entity.PerformanceGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PerformanceGoalRepository extends JpaRepository<PerformanceGoal, Integer> {
    List<PerformanceGoal> findByEmployeeIdAndCycleIdOrderByIdAsc(UUID employeeId, Integer cycleId);
    long countByEmployeeIdAndCycleId(UUID employeeId, Integer cycleId);
}