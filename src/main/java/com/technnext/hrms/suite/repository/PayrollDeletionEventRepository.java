package com.technnext.hrms.suite.repository;

import com.technnext.hrms.suite.entity.PayrollDeletionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PayrollDeletionEventRepository extends JpaRepository<PayrollDeletionEvent, UUID> { }

