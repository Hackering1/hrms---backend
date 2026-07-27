package com.technnext.hrms.document.repository;

import com.technnext.hrms.document.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Integer> {
    List<EmployeeDocument> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
    List<EmployeeDocument> findByExpiryDateBefore(LocalDate date);
    // File-access check: documents whose fileUrl embeds a given stored-file id.
    List<EmployeeDocument> findByFileUrlContaining(String fileIdFragment);
}