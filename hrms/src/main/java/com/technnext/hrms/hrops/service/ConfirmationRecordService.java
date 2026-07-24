package com.technnext.hrms.hrops.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.hrops.entity.ConfirmationRecord;
import com.technnext.hrms.hrops.repository.ConfirmationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConfirmationRecordService {

    private final ConfirmationRecordRepository repository;

    public List<ConfirmationRecord> getAll() { return repository.findAll(); }

    public List<ConfirmationRecord> getByEmployee(UUID employeeId) {
        return repository.findByEmployeeId(employeeId);
    }

    public ConfirmationRecord create(ConfirmationRecord input) {
        input.setId(null);
        return repository.save(input);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("ConfirmationRecord", id);
        repository.deleteById(id);
    }
}