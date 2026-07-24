package com.technnext.hrms.organization.repository;

import com.technnext.hrms.organization.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Integer> {
}