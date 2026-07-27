package com.technnext.hrms.organization.repository;

import com.technnext.hrms.organization.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Integer> {

    // Used by LeaveDaysCalculator to exclude public holidays from leave counts.
    List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
}