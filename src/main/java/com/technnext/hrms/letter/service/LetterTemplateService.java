package com.technnext.hrms.letter.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.letter.entity.LetterTemplate;
import com.technnext.hrms.letter.repository.LetterTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LetterTemplateService {

    private final LetterTemplateRepository repository;

    public List<LetterTemplate> getAll() { return repository.findAll(); }

    public LetterTemplate getById(Integer id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("LetterTemplate", id));
    }

    public LetterTemplate create(LetterTemplate input) {
        input.setId(null);
        return repository.save(input);
    }

    public LetterTemplate update(Integer id, LetterTemplate input) {
        LetterTemplate t = getById(id);
        t.setName(input.getName());
        t.setLetterType(input.getLetterType());
        t.setTemplateBody(input.getTemplateBody());
        t.setIsActive(input.getIsActive());
        return repository.save(t);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("LetterTemplate", id);
        repository.deleteById(id);
    }
}