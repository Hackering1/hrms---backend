package com.technnext.hrms.timesheet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Full replacement of a week's entries (only hours > 0 should be sent). */
public record TimesheetSaveRequest(List<EntryLine> entries) {
    public record EntryLine(Integer projectId, LocalDate workDate, BigDecimal hours, String description) {}
}