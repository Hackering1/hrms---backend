package com.technnext.hrms.organization.repository;

import com.technnext.hrms.organization.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Integer> {
}