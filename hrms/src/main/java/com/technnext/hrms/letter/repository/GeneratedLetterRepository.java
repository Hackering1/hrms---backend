package com.technnext.hrms.letter.repository;

import com.technnext.hrms.letter.entity.GeneratedLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface GeneratedLetterRepository extends JpaRepository<GeneratedLetter, Integer> {
    List<GeneratedLetter> findByEmployeeIdOrderByLetterDateDesc(UUID employeeId);
    // File-access check: letters whose fileUrl embeds a given stored-file id.
    List<GeneratedLetter> findByFileUrlContaining(String fileIdFragment);
}