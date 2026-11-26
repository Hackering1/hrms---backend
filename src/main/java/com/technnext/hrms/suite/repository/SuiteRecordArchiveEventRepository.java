package com.technnext.hrms.suite.repository;

import com.technnext.hrms.suite.entity.SuiteRecordArchiveEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SuiteRecordArchiveEventRepository extends JpaRepository<SuiteRecordArchiveEvent, UUID> { }

