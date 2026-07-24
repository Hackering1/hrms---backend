package com.technnext.hrms.organization.repository;

import com.technnext.hrms.organization.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, Integer> {
}