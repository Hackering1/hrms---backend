package com.technnext.hrms.file.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.file.entity.StoredFile;
import com.technnext.hrms.file.service.FileAccessService;
import com.technnext.hrms.file.service.FileStorageService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService service;
    private final FileAccessService fileAccessService;

    /**
     * Upload a file (stored in the database). Returns its id + a download url.
     * FIX: Validate principal is not null before accessing getUser() to avoid NPE
     *      when the filter chain somehow passes an unauthenticated request here
     *      (edge case with certain proxy setups).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UUID uploadedBy = (principal != null && principal.getUser() != null)
                ? principal.getUser().getId() : null;
        StoredFile sf = service.store(file, uploadedBy);
        return ApiResponse.ok("File uploaded", Map.of(
                "id", sf.getId(),
                "fileName", sf.getFileName() != null ? sf.getFileName() : "",
                "contentType", sf.getContentType() != null ? sf.getContentType() : "",
                "size", sf.getFileSize() != null ? sf.getFileSize() : 0L,
                "url", "/api/files/" + sf.getId()
        ));
    }

    /**
     * Download / view a stored file by id.
     * FIX 1: Guard against null data in the DB row to avoid NPE in ByteArrayResource.
     * FIX 2: Sanitise filename in Content-Disposition to prevent header injection
     *         (replace double-quotes and control chars).
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        StoredFile sf = service.load(id);

        // Access control: files back documents, letters, selfies and photos, so a
        // raw id must not be downloadable by anyone who happens to hold it.
        if (!fileAccessService.canDownload(sf, principal)) {
            throw new AccessDeniedException("You do not have access to this file.");
        }

        // FIX 1: null data guard
        byte[] data = sf.getData();
        if (data == null) data = new byte[0];

        String type = sf.getContentType() != null
                ? sf.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        // FIX 2: sanitise filename for Content-Disposition header
        String rawName = sf.getFileName() != null ? sf.getFileName() : "download";
        String safeName = rawName.replaceAll("[\"\\r\\n]", "_");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + safeName + "\"")
                .body(new ByteArrayResource(data));
    }
}