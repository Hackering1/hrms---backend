package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.payroll.entity.PtSlab;
import com.technnext.hrms.payroll.repository.PtSlabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PtSlabService {

    private final PtSlabRepository repository;

    @Transactional(readOnly = true)
    public List<PtSlab> getAll() {
        return repository.findAllByOrderByStateAscMinSalaryAsc();
    }

    @Transactional
    public PtSlab create(PtSlab body) {
        body.setId(null);
        if (body.getEffectiveFrom() == null) body.setEffectiveFrom(LocalDate.now());
        return repository.save(body);
    }

    @Transactional
    public PtSlab update(Integer id, PtSlab body) {
        PtSlab existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PT slab not found: " + id));
        existing.setState(body.getState());
        existing.setGender(body.getGender());
        existing.setMinSalary(body.getMinSalary());
        existing.setMaxSalary(body.getMaxSalary());
        existing.setMonthlyAmount(body.getMonthlyAmount());
        existing.setFebruaryAmount(body.getFebruaryAmount());
        existing.setIsActive(body.getIsActive());
        return repository.save(existing);
    }

    @Transactional
    public void delete(Integer id) {
        PtSlab existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PT slab not found: " + id));
        existing.setIsActive(false);
        repository.save(existing);
    }
}