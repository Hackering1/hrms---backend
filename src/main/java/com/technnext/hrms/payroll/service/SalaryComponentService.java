package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.payroll.entity.SalaryComponent;
import com.technnext.hrms.payroll.repository.SalaryComponentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SalaryComponentService {

    private final SalaryComponentRepository repository;

    @Transactional(readOnly = true)
    public List<SalaryComponent> getAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public SalaryComponent getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salary component not found: " + id));
    }

    @Transactional
    public SalaryComponent create(SalaryComponent body) {
        body.setId(null);
        return repository.save(body);
    }

    @Transactional
    public SalaryComponent update(Integer id, SalaryComponent body) {
        SalaryComponent existing = getById(id);
        existing.setName(body.getName());
        existing.setCode(body.getCode());
        existing.setComponentType(body.getComponentType());
        existing.setCalculationType(body.getCalculationType());
        existing.setDefaultPercentage(body.getDefaultPercentage());
        existing.setIsTaxable(body.getIsTaxable());
        existing.setIsStatutory(body.getIsStatutory());
        existing.setIsActive(body.getIsActive());
        existing.setDisplayOrder(body.getDisplayOrder());
        return repository.save(existing);
    }

    @Transactional
    public void delete(Integer id) {
        SalaryComponent existing = getById(id);
        // Soft-deactivate rather than hard-delete: components may already be referenced
        // by historical salary structures / payslip line items.
        existing.setIsActive(false);
        repository.save(existing);
    }
}