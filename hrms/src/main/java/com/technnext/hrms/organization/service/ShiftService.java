package com.technnext.hrms.organization.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.organization.entity.Shift;
import com.technnext.hrms.organization.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository repository;

    public List<Shift> getAll() { return repository.findAll(); }

    public Shift getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", id));
    }

    public Shift create(Shift s) {
        s.setId(null);
        return repository.save(s);
    }

    public Shift update(Integer id, Shift input) {
        Shift s = getById(id);
        s.setName(input.getName());
        s.setStartTime(input.getStartTime());
        s.setEndTime(input.getEndTime());
        s.setGraceMinutes(input.getGraceMinutes());
        s.setIsNightShift(input.getIsNightShift());
        s.setIsActive(input.getIsActive());
        return repository.save(s);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("Shift", id);
        repository.deleteById(id);
    }
}