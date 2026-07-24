package com.technnext.hrms.organization.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.organization.entity.Designation;
import com.technnext.hrms.organization.repository.DesignationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DesignationService {

    private final DesignationRepository repository;

    public List<Designation> getAll() { return repository.findAll(); }

    public Designation getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Designation", id));
    }

    public Designation create(Designation d) {
        d.setId(null);
        return repository.save(d);
    }

    public Designation update(Integer id, Designation input) {
        Designation d = getById(id);
        d.setName(input.getName());
        d.setCode(input.getCode());
        d.setDepartmentId(input.getDepartmentId());
        d.setLevel(input.getLevel());
        d.setIsActive(input.getIsActive());
        return repository.save(d);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("Designation", id);
        repository.deleteById(id);
    }
}