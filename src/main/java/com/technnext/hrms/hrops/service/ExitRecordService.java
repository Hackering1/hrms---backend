package com.technnext.hrms.hrops.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.hrops.entity.ExitRecord;
import com.technnext.hrms.hrops.repository.ExitRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExitRecordService {

    private final ExitRecordRepository repository;

    public List<ExitRecord> getAll() { return repository.findAll(); }

    public List<ExitRecord> getByEmployee(UUID employeeId) {
        return repository.findByEmployeeId(employeeId);
    }

    public ExitRecord create(ExitRecord input) {
        input.setId(null);
        return repository.save(input);
    }

    public ExitRecord update(Integer id, ExitRecord input) {
        ExitRecord e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExitRecord", id));
        e.setResignationDate(input.getResignationDate());
        e.setLastWorkingDate(input.getLastWorkingDate());
        e.setExitType(input.getExitType());
        e.setNoticePeriodDays(input.getNoticePeriodDays());
        e.setNoticeWaived(input.getNoticeWaived());
        e.setExitInterviewDone(input.getExitInterviewDone());
        e.setExitRemarks(input.getExitRemarks());
        return repository.save(e);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("ExitRecord", id);
        repository.deleteById(id);
    }
}