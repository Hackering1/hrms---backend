package com.technnext.hrms.plan.repository;

import com.technnext.hrms.plan.entity.CompanySubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, Integer> {
    Optional<CompanySubscription> findFirstByStatusOrderByIdDesc(String status);
    List<CompanySubscription> findAllByOrderByIdDesc();
}