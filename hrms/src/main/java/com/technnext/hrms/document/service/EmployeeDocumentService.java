package com.technnext.hrms.document.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.document.dto.EmployeeDocumentCreate;
import com.technnext.hrms.document.entity.EmployeeDocument;
import com.technnext.hrms.document.repository.EmployeeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeDocumentService {

    private final EmployeeDocumentRepository repository;

    public List<EmployeeDocument> getByEmployee(UUID employeeId) {
        return repository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    public EmployeeDocument getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EmployeeDocument", id));
    }

    public List<EmployeeDocument> getExpiringBefore(LocalDate date) {
        return repository.findByExpiryDateBefore(date);
    }

    public EmployeeDocument create(EmployeeDocumentCreate req) {
        EmployeeDocument doc = EmployeeDocument.builder()
                .employeeId(req.employeeId())
                .categoryId(req.categoryId())
                .documentName(req.documentName())
                .fileUrl(req.fileUrl())
                .fileType(req.fileType())
                .fileSizeKb(req.fileSizeKb())
                .expiryDate(req.expiryDate())
                .uploadedBy(req.uploadedBy())
                .expiryAlerted(false)
                .build();
        return repository.save(doc);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("EmployeeDocument", id);
        repository.deleteById(id);
    }
}