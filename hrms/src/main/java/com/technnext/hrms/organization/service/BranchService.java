package com.technnext.hrms.organization.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.organization.entity.Branch;
import com.technnext.hrms.organization.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository repository;

    public List<Branch> getAll() { return repository.findAll(); }

    public Branch getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
    }

    public Branch create(Branch branch) {
        branch.setId(null);
        return repository.save(branch);
    }

    public Branch update(Integer id, Branch input) {
        Branch b = getById(id);
        b.setName(input.getName());
        b.setCode(input.getCode());
        b.setAddress(input.getAddress());
        b.setCity(input.getCity());
        b.setState(input.getState());
        b.setCountry(input.getCountry());
        b.setPincode(input.getPincode());
        b.setIsActive(input.getIsActive());
        return repository.save(b);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("Branch", id);
        repository.deleteById(id);
    }
}