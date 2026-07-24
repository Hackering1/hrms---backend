package com.technnext.hrms.letter.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.letter.entity.LetterTemplate;
import com.technnext.hrms.letter.service.LetterTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/letter-templates")
@RequiredArgsConstructor
public class LetterTemplateController {

    private final LetterTemplateService service;

    @GetMapping
    public ApiResponse<List<LetterTemplate>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<LetterTemplate> getById(@PathVariable Integer id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<LetterTemplate> create(@RequestBody LetterTemplate body) {
        return ApiResponse.ok("Template created", service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<LetterTemplate> update(@PathVariable Integer id, @RequestBody LetterTemplate body) {
        return ApiResponse.ok("Template updated", service.update(id, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}