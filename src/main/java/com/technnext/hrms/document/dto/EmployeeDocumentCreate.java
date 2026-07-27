package com.technnext.hrms.document.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeDocumentCreate(
        UUID employeeId,
        Integer categoryId,
        String documentName,
        String fileUrl,
        String fileType,
        Integer fileSizeKb,
        LocalDate expiryDate,
        UUID uploadedBy
) {}