package com.technnext.hrms.letter.repository;

import com.technnext.hrms.letter.entity.LetterTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LetterTemplateRepository extends JpaRepository<LetterTemplate, Integer> {
    List<LetterTemplate> findByLetterType(String letterType);
}