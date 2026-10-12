package com.technnext.hrms.plan.repository;

import com.technnext.hrms.plan.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Integer> {
    List<SubscriptionPlan> findAllByOrderByPriceMonthlyAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByCodeIgnoreCase(String code);
}