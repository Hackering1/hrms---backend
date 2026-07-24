package com.technnext.hrms.hrops.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.hrops.entity.OnboardingChecklist;
import com.technnext.hrms.hrops.repository.OnboardingChecklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingChecklistService {

    private final OnboardingChecklistRepository repository;

    public List<OnboardingChecklist> getByEmployee(UUID employeeId) {
        return repository.findByEmployeeIdOrderByDueDateAsc(employeeId);
    }

    public OnboardingChecklist getById(Integer id) {
        return repository.findById(id).orElseThrow(
                () -> new com.technnext.hrms.common.exception.ResourceNotFoundException("OnboardingChecklist", id));
    }

    public OnboardingChecklist create(OnboardingChecklist input) {
        input.setId(null);
        return repository.save(input);
    }

    public OnboardingChecklist update(Integer id, OnboardingChecklist input) {
        OnboardingChecklist t = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardingTask", id));
        t.setTaskName(input.getTaskName());
        t.setCategory(input.getCategory());
        t.setDueDate(input.getDueDate());
        t.setRemarks(input.getRemarks());
        return repository.save(t);
    }

    public OnboardingChecklist setCompleted(Integer id, boolean completed, UUID completedBy) {
        OnboardingChecklist t = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardingTask", id));
        t.setIsCompleted(completed);
        t.setCompletedAt(completed ? LocalDateTime.now() : null);
        t.setCompletedBy(completed ? completedBy : null);
        return repository.save(t);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("OnboardingTask", id);
        repository.deleteById(id);
    }
}