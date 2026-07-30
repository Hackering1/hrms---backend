package com.technnext.hrms.document.repository;

import com.technnext.hrms.document.entity.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Integer> {
    Optional<DocumentCategory> findByNameIgnoreCase(String name);
}