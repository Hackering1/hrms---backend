package com.technnext.hrms.timesheet.repository;

import com.technnext.hrms.timesheet.entity.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimesheetRepository extends JpaRepository<Timesheet, Integer> {
    Optional<Timesheet> findByEmployeeIdAndWeekStart(UUID employeeId, LocalDate weekStart);
    List<Timesheet> findByEmployeeIdOrderByWeekStartDesc(UUID employeeId);
    List<Timesheet> findByStatusOrderByWeekStartDesc(String status);
}