package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.payroll.entity.PfSettings;
import com.technnext.hrms.payroll.repository.PfSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PfSettingsService {

    private final PfSettingsRepository repository;

    @Transactional(readOnly = true)
    public List<PfSettings> getAll() {
        return repository.findAllByOrderByEffectiveFromDesc();
    }

    @Transactional(readOnly = true)
    public PfSettings current() {
        List<PfSettings> rows = repository.findAllByOrderByEffectiveFromDesc();
        if (rows.isEmpty()) throw new ResourceNotFoundException("No PF settings configured.");
        return rows.get(0);
    }

    // A new PF settings row is a REVISION (e.g. EPFO raises the wage ceiling) — keep the
    // history rather than overwrite, same idea as EmployeeSalary effective-dating.
    @Transactional
    public PfSettings create(PfSettings body) {
        body.setId(null);
        if (body.getEffectiveFrom() == null) body.setEffectiveFrom(LocalDate.now());
        return repository.save(body);
    }
}