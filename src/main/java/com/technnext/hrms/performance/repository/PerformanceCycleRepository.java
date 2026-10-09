package com.technnext.hrms.performance.repository;

import com.technnext.hrms.performance.entity.PerformanceCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PerformanceCycleRepository extends JpaRepository<PerformanceCycle, Integer> {
    List<PerformanceCycle> findAllByOrderByStartDateDesc();
    boolean existsByNameIgnoreCase(String name);
}