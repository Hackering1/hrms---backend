package com.technnext.hrms.file.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.file.entity.StoredFile;
import com.technnext.hrms.file.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final StoredFileRepository repository;

    private static final List<String> ALLOWED = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/png", "image/jpeg", "image/jpg"
    );

    /**
     * Maximum allowed file size in bytes (10 MB).
     * FIX: Added an explicit size check here so even if the multipart limit is raised,
     *      the service layer still enforces a sane ceiling and returns 400, not 500.
     */
    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private String maxFileSizeRaw;

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB

    @Transactional
    public StoredFile store(MultipartFile file, UUID uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file provided");
        }

        // FIX: explicit size guard in the service layer
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("File size exceeds the 10 MB limit.");
        }

        String type = file.getContentType();
        if (type != null && !ALLOWED.contains(type)) {
            throw new BadRequestException(
                "Unsupported file type: " + type + ". Allowed: PDF, DOC, DOCX, PNG, JPG.");
        }

        // FIX: Sanitise the original filename to prevent path-traversal or null filename
        //      causing issues when the file is later served via Content-Disposition.
        String originalName = file.getOriginalFilename();
        String safeFileName = (originalName != null && !originalName.isBlank())
                ? originalName.replaceAll("[^a-zA-Z0-9.\\-_]", "_")
                : "upload_" + UUID.randomUUID();

        try {
            StoredFile sf = StoredFile.builder()
                    .fileName(safeFileName)
                    .contentType(type)
                    .fileSize(file.getSize())
                    .data(file.getBytes())
                    .uploadedBy(uploadedBy)
                    .build();
            return repository.save(sf);
        } catch (IOException e) {
            throw new BadRequestException("Could not read uploaded file: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StoredFile load(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File", id));
    }
}