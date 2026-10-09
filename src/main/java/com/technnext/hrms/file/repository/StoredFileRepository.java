package com.technnext.hrms.file.repository;

import com.technnext.hrms.file.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    /**
     * Counts stored files with this id that were uploaded by a DIFFERENT user.
     * Used to stop a document record from being attached to someone else's file.
     * Selects only a count (never the bytea column), so it is cheap.
     */
    @Query("select count(f) from StoredFile f "
            + "where f.id = :id and f.uploadedBy is not null and f.uploadedBy <> :uploader")
    long countUploadedByOthers(@Param("id") UUID id, @Param("uploader") UUID uploader);

    /** 1 only if this file exists AND was uploaded by exactly this user (derived query; never loads the bytes). */
    long countByIdAndUploadedBy(UUID id, UUID uploadedBy);
}