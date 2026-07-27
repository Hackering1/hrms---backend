package com.technnext.hrms.organization.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.organization.entity.Holiday;
import com.technnext.hrms.organization.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository repository;

    public List<Holiday> getAll() { return repository.findAll(); }

    public Holiday getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", id));
    }

    public Holiday create(Holiday h) {
        h.setId(null);
        return repository.save(h);
    }

    public Holiday update(Integer id, Holiday input) {
        Holiday h = getById(id);
        h.setName(input.getName());
        h.setHolidayDate(input.getHolidayDate());
        h.setType(input.getType());
        h.setDescription(input.getDescription());
        return repository.save(h);
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) throw new ResourceNotFoundException("Holiday", id);
        repository.deleteById(id);
    }
}