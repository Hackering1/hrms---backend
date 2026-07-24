package com.technnext.hrms.organization.repository;

import com.technnext.hrms.organization.entity.Designation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignationRepository extends JpaRepository<Designation, Integer> {
}