package com.technnext.hrms.document.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.document.entity.DocumentCategory;
import com.technnext.hrms.document.service.DocumentCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/document-categories")
@RequiredArgsConstructor
public class DocumentCategoryController {

    private final DocumentCategoryService service;

    @GetMapping
    public ApiResponse<List<DocumentCategory>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentCategory> getById(@PathVariable Integer id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<DocumentCategory> create(@Valid @RequestBody DocumentCategory body) {
        return ApiResponse.ok("Created", service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<DocumentCategory> update(@PathVariable Integer id, @Valid @RequestBody DocumentCategory body) {
        return ApiResponse.ok("Updated", service.update(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}