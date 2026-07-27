package com.technnext.hrms.hrops.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.hrops.entity.ProbationTracking;
import com.technnext.hrms.hrops.repository.ProbationTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProbationTrackingService {

    private final ProbationTrackingRepository repository;

    public List<ProbationTracking> getAll() { return repository.findAll(); }

    public List<ProbationTracking> getByEmployee(UUID employeeId) {
        return repository.findByEmployeeId(employeeId);
    }

    public ProbationTracking getById(Integer id) {
        return repository.findById(id).orElseThrow(
                () -> new com.technnext.hrms.common.exception.ResourceNotFoundException("ProbationTracking", id));
    }

    public ProbationTracking create(ProbationTracking input) {
        input.setId(null);
        if (input.getStatus() == null) input.setStatus("IN_PROGRESS");
        return repository.save(input);
    }

    public ProbationTracking review(Integer id, String status, String notes, UUID reviewedBy) {
        ProbationTracking p = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProbationTracking", id));
        p.setStatus(status);
        p.setReviewNotes(notes);
        p.setReviewedBy(reviewedBy);
        p.setReviewedAt(LocalDateTime.now());
        return repository.save(p);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("ProbationTracking", id);
        repository.deleteById(id);
    }
}