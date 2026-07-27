package com.technnext.hrms.audit.repository;

import com.technnext.hrms.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Most-recent-first, useful for an admin audit viewer later.
    List<AuditLog> findAllByOrderByCreatedAtDesc();

    List<AuditLog> findByModuleOrderByCreatedAtDesc(String module);

    List<AuditLog> findByRecordIdOrderByCreatedAtDesc(String recordId);
}