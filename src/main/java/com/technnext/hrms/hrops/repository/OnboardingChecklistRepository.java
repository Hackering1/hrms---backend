package com.technnext.hrms.hrops.repository;

import com.technnext.hrms.hrops.entity.OnboardingChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OnboardingChecklistRepository extends JpaRepository<OnboardingChecklist, Integer> {
    List<OnboardingChecklist> findByEmployeeIdOrderByDueDateAsc(UUID employeeId);
}