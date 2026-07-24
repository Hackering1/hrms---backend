package com.technnext.hrms.leave.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.leave.entity.LeaveType;
import com.technnext.hrms.leave.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveTypeService {

    private final LeaveTypeRepository repository;

    public List<LeaveType> getAll() { return repository.findAll(); }

    public LeaveType getById(Integer id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("LeaveType", id));
    }

    public LeaveType create(LeaveType input) {
        input.setId(null);
        return repository.save(input);
    }

    public LeaveType update(Integer id, LeaveType input) {
        LeaveType t = getById(id);
        t.setName(input.getName());
        t.setCode(input.getCode());
        t.setDaysPerYear(input.getDaysPerYear());
        t.setIsCarryForward(input.getIsCarryForward());
        t.setMaxCarryForward(input.getMaxCarryForward());
        t.setIsPaid(input.getIsPaid());
        t.setApplicableGender(input.getApplicableGender());
        t.setRequiresDocument(input.getRequiresDocument());
        t.setMinDaysNotice(input.getMinDaysNotice());
        t.setIsActive(input.getIsActive());
        return repository.save(t);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("LeaveType", id);
        repository.deleteById(id);
    }
}