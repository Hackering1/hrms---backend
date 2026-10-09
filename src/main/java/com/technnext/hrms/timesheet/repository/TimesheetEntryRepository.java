package com.technnext.hrms.timesheet.repository;

import com.technnext.hrms.timesheet.entity.TimesheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, Integer> {
    List<TimesheetEntry> findByTimesheetIdOrderByWorkDateAscIdAsc(Integer timesheetId);
    List<TimesheetEntry> findByTimesheetIdIn(List<Integer> timesheetIds);
    void deleteByTimesheetId(Integer timesheetId);
    boolean existsByProjectId(Integer projectId);
}