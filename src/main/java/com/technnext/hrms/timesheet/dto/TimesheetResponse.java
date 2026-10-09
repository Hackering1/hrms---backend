package com.technnext.hrms.timesheet.dto;

import com.technnext.hrms.timesheet.entity.Timesheet;
import com.technnext.hrms.timesheet.entity.TimesheetEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** id is null for a week that has not been saved yet (status DRAFT, no entries). */
public record TimesheetResponse(
        Integer id,
        UUID employeeId,
        LocalDate weekStart,
        String status,
        BigDecimal totalHours,
        LocalDateTime submittedAt,
        UUID reviewedBy,
        LocalDateTime reviewedAt,
        String reviewerRemarks,
        List<EntryResponse> entries
) {
    public record EntryResponse(Integer id, Integer projectId, LocalDate workDate, BigDecimal hours, String description) {
        public static EntryResponse from(TimesheetEntry e) {
            return new EntryResponse(e.getId(), e.getProjectId(), e.getWorkDate(), e.getHours(), e.getDescription());
        }
    }

    public static TimesheetResponse from(Timesheet t, List<TimesheetEntry> entries) {
        BigDecimal total = entries.stream().map(TimesheetEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TimesheetResponse(t.getId(), t.getEmployeeId(), t.getWeekStart(), t.getStatus(), total,
                t.getSubmittedAt(), t.getReviewedBy(), t.getReviewedAt(), t.getReviewerRemarks(),
                entries.stream().map(EntryResponse::from).toList());
    }

    public static TimesheetResponse empty(UUID employeeId, LocalDate weekStart) {
        return new TimesheetResponse(null, employeeId, weekStart, "DRAFT", BigDecimal.ZERO,
                null, null, null, null, List.of());
    }
}