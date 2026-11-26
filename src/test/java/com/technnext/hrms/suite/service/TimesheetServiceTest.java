package com.technnext.hrms.suite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.suite.entity.SuiteRecord;
import com.technnext.hrms.suite.repository.SuiteRecordRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TimesheetServiceTest {
    @Test
    void generatesIsoWeekTimesheetFromStoppedSessionsOnly() {
        SuiteRecordRepository repository = Mockito.mock(SuiteRecordRepository.class);
        UUID employeeId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 11, 9);
        when(repository.existsByModuleAndEmployeeIdAndPeriod("timesheets", employeeId, "2026-W46"))
                .thenReturn(false);
        when(repository.findByModuleAndEmployeeIdAndStartDateBetweenOrderByCreatedAtAsc(
                "time-tracking", employeeId, start, start.plusDays(6)))
                .thenReturn(List.of(
                        session("Client implementation", "STOPPED", 95, start),
                        session("Planning", "STOPPED", 35, start.plusDays(1)),
                        session("Still running", "RUNNING", 50, start.plusDays(2)),
                        session("Cross-week timer", "STOPPED", 600, start.plusDays(6), start.plusDays(7))));
        when(repository.save(any(SuiteRecord.class))).thenAnswer(call -> call.getArgument(0));

        SuiteRecord result = new TimesheetService(repository)
                .generateFromCompletedTimers(employeeId, "2026-W46", actorId);

        assertEquals("timesheets", result.getModule());
        assertEquals("SUBMITTED", result.getStatus());
        assertEquals(130, result.getMinutes());
        assertEquals("2026-W46", result.getPeriod());
        assertEquals(start, result.getStartDate());
        assertEquals(start.plusDays(6), result.getEndDate());
        assertEquals(employeeId, result.getEmployeeId());
        assertEquals(actorId, result.getCreatedBy());
    }

    @Test
    void rejectsWeekNumbersNotPresentInTheSelectedIsoYear() {
        assertThrows(BadRequestException.class, () -> TimesheetService.weekStart("2025-W53"));
    }

    private SuiteRecord session(String title, String status, Integer minutes, LocalDate startDate) {
        return session(title, status, minutes, startDate, startDate);
    }

    private SuiteRecord session(String title, String status, Integer minutes, LocalDate startDate, LocalDate endDate) {
        return SuiteRecord.builder()
                .module("time-tracking")
                .title(title)
                .status(status)
                .minutes(minutes)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }
}
