package com.technnext.hrms.suite.repository;

import com.technnext.hrms.suite.entity.PayrollProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PayrollProfileRepository extends JpaRepository<PayrollProfile, UUID> {
    List<PayrollProfile> findByEmployeeIdIn(List<UUID> employeeIds);
}
