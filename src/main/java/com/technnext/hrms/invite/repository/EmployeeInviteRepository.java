package com.technnext.hrms.invite.repository;

import com.technnext.hrms.invite.entity.EmployeeInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeInviteRepository extends JpaRepository<EmployeeInvite, UUID> {
    Optional<EmployeeInvite> findByToken(String token);

    List<EmployeeInvite> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    List<EmployeeInvite> findAllByOrderByCreatedAtDesc();
}