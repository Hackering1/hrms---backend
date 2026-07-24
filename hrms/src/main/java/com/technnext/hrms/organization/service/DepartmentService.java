package com.technnext.hrms.organization.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.organization.entity.Department;
import com.technnext.hrms.organization.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository repository;

    public List<Department> getAll() { return repository.findAll(); }

    public Department getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    public Department create(Department d) {
        d.setId(null);
        return repository.save(d);
    }

    public Department update(Integer id, Department input) {
        Department d = getById(id);
        d.setName(input.getName());
        d.setCode(input.getCode());
        d.setBranchId(input.getBranchId());
        d.setDescription(input.getDescription());
        d.setIsActive(input.getIsActive());
        return repository.save(d);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("Department", id);
        repository.deleteById(id);
    }
}