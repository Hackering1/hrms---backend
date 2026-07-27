package com.technnext.hrms.file.repository;

import com.technnext.hrms.file.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
}