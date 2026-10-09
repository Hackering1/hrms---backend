package com.technnext.hrms.timesheet.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.timesheet.dto.ProjectRequest;
import com.technnext.hrms.timesheet.dto.TimesheetDecision;
import com.technnext.hrms.timesheet.dto.TimesheetResponse;
import com.technnext.hrms.timesheet.dto.TimesheetSaveRequest;
import com.technnext.hrms.timesheet.entity.Timesheet;
import com.technnext.hrms.timesheet.entity.TimesheetEntry;
import com.technnext.hrms.timesheet.entity.TimesheetProject;
import com.technnext.hrms.timesheet.repository.TimesheetEntryRepository;
import com.technnext.hrms.timesheet.repository.TimesheetProjectRepository;
import com.technnext.hrms.timesheet.repository.TimesheetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Weekly timesheets. An employee logs hours per project per day for one ISO week
 * (Monday start), submits the week, and a manager / HR approves or rejects it.
 * A rejected week can be edited and submitted again; an approved week is locked.
 */
@Service
@RequiredArgsConstructor
public class TimesheetService {

    private static final BigDecimal MAX_HOURS_PER_DAY = new BigDecimal("24");
    private static final int MAX_ENTRIES_PER_WEEK = 200;

    private final TimesheetRepository timesheetRepository;
    private final TimesheetEntryRepository entryRepository;
    private final TimesheetProjectRepository projectRepository;

    // ── Projects ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TimesheetProject> listProjects(boolean activeOnly) {
        return activeOnly ? projectRepository.findByIsActiveTrueOrderByNameAsc()
                          : projectRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public TimesheetProject createProject(ProjectRequest req) {
        String name = clean(req.name());
        String code = clean(req.code());
        if (name == null || code == null) throw new BadRequestException("Project name and code are required.");
        if (name.length() > 150 || code.length() > 40) throw new BadRequestException("Project name (max 150) or code (max 40) is too long.");
        if (projectRepository.existsByNameIgnoreCase(name)) throw new BadRequestException("A project named '" + name + "' already exists.");
        if (projectRepository.existsByCodeIgnoreCase(code)) throw new BadRequestException("A project with code '" + code + "' already exists.");
        return projectRepository.save(TimesheetProject.builder()
                .name(name).code(code.toUpperCase())
                .isActive(req.isActive() == null ? true : req.isActive())
                .build());
    }

    @Transactional
    public TimesheetProject updateProject(Integer id, ProjectRequest req) {
        TimesheetProject p = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        String name = clean(req.name());
        String code = clean(req.code());
        if (name == null || code == null) throw new BadRequestException("Project name and code are required.");
        if (name.length() > 150 || code.length() > 40) throw new BadRequestException("Project name (max 150) or code (max 40) is too long.");
        if (!name.equalsIgnoreCase(p.getName()) && projectRepository.existsByNameIgnoreCase(name))
            throw new BadRequestException("A project named '" + name + "' already exists.");
        if (!code.equalsIgnoreCase(p.getCode()) && projectRepository.existsByCodeIgnoreCase(code))
            throw new BadRequestException("A project with code '" + code + "' already exists.");
        p.setName(name);
        p.setCode(code.toUpperCase());
        if (req.isActive() != null) p.setIsActive(req.isActive());
        return projectRepository.save(p);
    }

    // ── Employee: read / save / submit ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public TimesheetResponse getWeek(UUID employeeId, LocalDate weekStart) {
        requireMonday(weekStart);
        return timesheetRepository.findByEmployeeIdAndWeekStart(employeeId, weekStart)
                .map(t -> TimesheetResponse.from(t, entryRepository.findByTimesheetIdOrderByWorkDateAscIdAsc(t.getId())))
                .orElseGet(() -> TimesheetResponse.empty(employeeId, weekStart));
    }

    @Transactional
    public TimesheetResponse saveWeek(UUID employeeId, LocalDate weekStart, TimesheetSaveRequest req) {
        requireMonday(weekStart);
        List<TimesheetSaveRequest.EntryLine> lines = req == null || req.entries() == null ? List.of() : req.entries();
        validateLines(weekStart, lines);

        Timesheet t = timesheetRepository.findByEmployeeIdAndWeekStart(employeeId, weekStart)
                .orElseGet(() -> Timesheet.builder().employeeId(employeeId).weekStart(weekStart).status("DRAFT").build());
        if ("SUBMITTED".equals(t.getStatus())) {
            throw new BadRequestException("This timesheet is awaiting approval and can't be edited.");
        }
        if ("APPROVED".equals(t.getStatus())) {
            throw new BadRequestException("This timesheet has been approved and is locked.");
        }
        // Editing a rejected sheet puts it back to DRAFT (the reviewer's remarks stay visible until re-submission).
        t.setStatus("DRAFT");
        t = timesheetRepository.save(t);

        entryRepository.deleteByTimesheetId(t.getId());
        entryRepository.flush();
        for (TimesheetSaveRequest.EntryLine l : lines) {
            entryRepository.save(TimesheetEntry.builder()
                    .timesheetId(t.getId())
                    .projectId(l.projectId())
                    .workDate(l.workDate())
                    .hours(l.hours().setScale(2, RoundingMode.HALF_UP))
                    .description(clean(l.description()))
                    .build());
        }
        return getWeek(employeeId, weekStart);
    }

    @Transactional
    public TimesheetResponse submitWeek(UUID employeeId, LocalDate weekStart) {
        requireMonday(weekStart);
        if (weekStart.isAfter(LocalDate.now())) {
            throw new BadRequestException("You can't submit a timesheet for a future week.");
        }
        Timesheet t = timesheetRepository.findByEmployeeIdAndWeekStart(employeeId, weekStart)
                .orElseThrow(() -> new BadRequestException("Nothing to submit — save some hours for this week first."));
        if (!"DRAFT".equals(t.getStatus()) && !"REJECTED".equals(t.getStatus())) {
            throw new BadRequestException("Only a draft or rejected timesheet can be submitted (this one is " + t.getStatus() + ").");
        }
        List<TimesheetEntry> entries = entryRepository.findByTimesheetIdOrderByWorkDateAscIdAsc(t.getId());
        BigDecimal total = entries.stream().map(TimesheetEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new BadRequestException("A timesheet with no hours can't be submitted.");
        }
        t.setStatus("SUBMITTED");
        t.setSubmittedAt(LocalDateTime.now());
        t.setReviewedBy(null);
        t.setReviewedAt(null);
        t.setReviewerRemarks(null);
        timesheetRepository.save(t);
        return TimesheetResponse.from(t, entries);
    }

    // ── Read lists ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TimesheetResponse> history(UUID employeeId) {
        return withEntries(timesheetRepository.findByEmployeeIdOrderByWeekStartDesc(employeeId));
    }

    @Transactional(readOnly = true)
    public List<TimesheetResponse> pending() {
        return withEntries(timesheetRepository.findByStatusOrderByWeekStartDesc("SUBMITTED"));
    }

    @Transactional(readOnly = true)
    public TimesheetResponse getById(Integer id) {
        Timesheet t = find(id);
        return TimesheetResponse.from(t, entryRepository.findByTimesheetIdOrderByWorkDateAscIdAsc(id));
    }

    // ── Approval ────────────────────────────────────────────────────────────────

    /** @param reviewerEmployeeId the approver's employee id, or null for a profile-less admin account */
    @Transactional
    public TimesheetResponse decide(Integer id, UUID reviewerEmployeeId, TimesheetDecision d) {
        Timesheet t = find(id);
        String status = d == null || d.status() == null ? "" : d.status().trim().toUpperCase();
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new BadRequestException("Decision must be APPROVED or REJECTED.");
        }
        if (!"SUBMITTED".equals(t.getStatus())) {
            throw new BadRequestException("Only a submitted timesheet can be reviewed (this one is " + t.getStatus() + ").");
        }
        if (reviewerEmployeeId != null && reviewerEmployeeId.equals(t.getEmployeeId())) {
            throw new BadRequestException("You can't review your own timesheet.");
        }
        String remarks = clean(d.remarks());
        if ("REJECTED".equals(status) && remarks == null) {
            throw new BadRequestException("Please give a reason when rejecting a timesheet.");
        }
        if (remarks != null && remarks.length() > 1000) {
            throw new BadRequestException("Remarks are too long (max 1000 characters).");
        }
        t.setStatus(status);
        t.setReviewedBy(reviewerEmployeeId);
        t.setReviewedAt(LocalDateTime.now());
        t.setReviewerRemarks(remarks);
        timesheetRepository.save(t);
        return TimesheetResponse.from(t, entryRepository.findByTimesheetIdOrderByWorkDateAscIdAsc(id));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private Timesheet find(Integer id) {
        return timesheetRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Timesheet", id));
    }

    private List<TimesheetResponse> withEntries(List<Timesheet> sheets) {
        if (sheets.isEmpty()) return List.of();
        Map<Integer, List<TimesheetEntry>> byTs = entryRepository
                .findByTimesheetIdIn(sheets.stream().map(Timesheet::getId).toList()).stream()
                .collect(Collectors.groupingBy(TimesheetEntry::getTimesheetId));
        return sheets.stream()
                .map(t -> TimesheetResponse.from(t, byTs.getOrDefault(t.getId(), List.of()).stream()
                        .sorted(java.util.Comparator.comparing(TimesheetEntry::getWorkDate)).toList()))
                .toList();
    }

    private void requireMonday(LocalDate weekStart) {
        if (weekStart == null) throw new BadRequestException("weekStart is required.");
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BadRequestException("weekStart must be a Monday (got " + weekStart + ").");
        }
    }

    private void validateLines(LocalDate weekStart, List<TimesheetSaveRequest.EntryLine> lines) {
        if (lines.size() > MAX_ENTRIES_PER_WEEK) {
            throw new BadRequestException("Too many entries for one week (max " + MAX_ENTRIES_PER_WEEK + ").");
        }
        LocalDate weekEnd = weekStart.plusDays(6);
        Map<LocalDate, BigDecimal> perDay = new HashMap<>();
        Set<Integer> projectIds = new HashSet<>();
        for (TimesheetSaveRequest.EntryLine l : lines) {
            if (l == null || l.projectId() == null) throw new BadRequestException("Every entry needs a project.");
            if (l.workDate() == null || l.workDate().isBefore(weekStart) || l.workDate().isAfter(weekEnd)) {
                throw new BadRequestException("Entry date " + (l == null ? null : l.workDate())
                        + " is outside the week " + weekStart + " to " + weekEnd + ".");
            }
            if (l.hours() == null || l.hours().signum() <= 0) {
                throw new BadRequestException("Hours must be greater than 0 (leave empty days out).");
            }
            if (l.hours().compareTo(MAX_HOURS_PER_DAY) > 0) {
                throw new BadRequestException("A single entry can't exceed 24 hours.");
            }
            if (l.description() != null && l.description().length() > 500) {
                throw new BadRequestException("Entry notes are too long (max 500 characters).");
            }
            perDay.merge(l.workDate(), l.hours(), BigDecimal::add);
            projectIds.add(l.projectId());
        }
        for (Map.Entry<LocalDate, BigDecimal> e : perDay.entrySet()) {
            if (e.getValue().compareTo(MAX_HOURS_PER_DAY) > 0) {
                throw new BadRequestException("More than 24 hours logged on " + e.getKey() + ".");
            }
        }
        Map<Integer, TimesheetProject> projects = projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(TimesheetProject::getId, p -> p));
        for (Integer pid : projectIds) {
            TimesheetProject p = projects.get(pid);
            if (p == null) throw new BadRequestException("Unknown project id: " + pid);
            if (!Boolean.TRUE.equals(p.getIsActive())) {
                throw new BadRequestException("Project '" + p.getName() + "' is inactive and can't be used for new entries.");
            }
        }
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}