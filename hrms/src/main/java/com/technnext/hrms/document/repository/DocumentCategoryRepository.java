package com.technnext.hrms.document.repository;

import com.technnext.hrms.document.entity.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Integer> {
}