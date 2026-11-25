package com.technnext.hrms.suite.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.suite.entity.SuiteRecord;
import com.technnext.hrms.suite.repository.SuiteRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimesheetService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SuiteRecordRepository repository;

    @Transactional
    public SuiteRecord generateFromCompletedTimers(UUID employeeId, String period, UUID actorId) {
        LocalDate start = weekStart(period);
        LocalDate end = start.plusDays(6);
        if (repository.existsByModuleAndEmployeeIdAndPeriod("timesheets", employeeId, period)) {
            throw new BadRequestException("A timesheet already exists for this employee and week.");
        }

        List<SuiteRecord> sessions = repository
                .findByModuleAndEmployeeIdAndStartDateBetweenOrderByCreatedAtAsc(
                        "time-tracking", employeeId, start, end)
                .stream()
                .filter(record -> "STOPPED".equals(record.getStatus()))
                .filter(record -> record.getMinutes() != null && record.getMinutes() > 0)
                // A timer that crosses the selected week boundary must not be
                // charged entirely to the week in which it started.
                .filter(record -> record.getEndDate() == null || !record.getEndDate().isAfter(end))
                .toList();
        long totalMinutes = sessions.stream().mapToLong(SuiteRecord::getMinutes).sum();
        if (totalMinutes == 0) {
            throw new BadRequestException("No completed time-tracker sessions were found for this week.");
        }
        if (totalMinutes > Integer.MAX_VALUE) {
            throw new BadRequestException("The recorded time exceeds the timesheet limit for one week.");
        }

        List<Map<String, Object>> entries = sessions.stream().map(session -> Map.<String, Object>of(
                "activity", session.getTitle(),
                "date", session.getStartDate().toString(),
                "minutes", session.getMinutes())).toList();
        final String details;
        try {
            details = JSON.writeValueAsString(entries);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the generated timesheet.", ex);
        }

        return repository.save(SuiteRecord.builder()
                .module("timesheets")
                .title("Weekly timesheet " + period)
                .employeeId(employeeId)
                .status("SUBMITTED")
                .period(period)
                .minutes((int) totalMinutes)
                .startDate(start)
                .endDate(end)
                .details(details)
                .createdBy(actorId)
                .build());
    }

    static LocalDate weekStart(String period) {
        if (period == null || !period.matches("\\d{4}-W(0[1-9]|[1-4][0-9]|5[0-3])")) {
            throw new BadRequestException("Choose a valid ISO week, such as 2026-W46.");
        }
        int year = Integer.parseInt(period.substring(0, 4));
        int week = Integer.parseInt(period.substring(6));
        WeekFields iso = WeekFields.ISO;
        LocalDate start = LocalDate.of(year, 1, 4)
                .with(iso.weekOfWeekBasedYear(), week)
                .with(iso.dayOfWeek(), DayOfWeek.MONDAY.getValue());
        if (start.get(iso.weekBasedYear()) != year || start.get(iso.weekOfWeekBasedYear()) != week) {
            throw new BadRequestException("That year does not contain the selected ISO week.");
        }
        return start;
    }
}
