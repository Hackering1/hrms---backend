package com.technnext.hrms.document.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.document.entity.DocumentCategory;
import com.technnext.hrms.document.repository.DocumentCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentCategoryService {

    private final DocumentCategoryRepository repository;

    public List<DocumentCategory> getAll() { return repository.findAll(); }

    public DocumentCategory getById(Integer id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("DocumentCategory", id));
    }

    public DocumentCategory create(DocumentCategory input) {
        input.setId(null);
        return repository.save(input);
    }

    public DocumentCategory update(Integer id, DocumentCategory input) {
        DocumentCategory c = getById(id);
        c.setName(input.getName());
        c.setDescription(input.getDescription());
        c.setHasExpiry(input.getHasExpiry());
        c.setIsActive(input.getIsActive());
        return repository.save(c);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("DocumentCategory", id);
        repository.deleteById(id);
    }
}