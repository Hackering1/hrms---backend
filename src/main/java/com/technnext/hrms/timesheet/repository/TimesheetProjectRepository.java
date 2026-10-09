package com.technnext.hrms.timesheet.repository;

import com.technnext.hrms.timesheet.entity.TimesheetProject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TimesheetProjectRepository extends JpaRepository<TimesheetProject, Integer> {
    List<TimesheetProject> findAllByOrderByNameAsc();
    List<TimesheetProject> findByIsActiveTrueOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByCodeIgnoreCase(String code);
}