package com.technnext.hrms.suite.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.suite.entity.SuiteRecord;
import com.technnext.hrms.suite.entity.SuiteRecordArchiveEvent;
import com.technnext.hrms.suite.repository.SuiteRecordArchiveEventRepository;
import com.technnext.hrms.suite.repository.SuiteRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reversibly hides payroll and its payslip while preserving the financial audit record. */
@Service
@RequiredArgsConstructor
public class PayrollArchiveService {
    private final SuiteRecordRepository repository;
    private final SuiteRecordArchiveEventRepository events;

    @Transactional
    public SuiteRecord archive(UUID id, UUID actorId) {
        SuiteRecord payroll = repository.lockByIdAndModule(id, "payroll")
                .orElseThrow(() -> new ResourceNotFoundException("Payroll record", id));
        if (payroll.getArchivedAt() != null) throw new BadRequestException("This payroll record is already archived.");
        try {
            if (!YearMonth.parse(payroll.getPeriod()).isBefore(YearMonth.now(ZoneId.of("Asia/Kolkata"))))
                throw new BadRequestException("Only payroll from a completed month can be archived.");
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new BadRequestException("Payroll period is invalid; this record cannot be archived.");
        }
        if ("EMAIL_SENDING".equals(payroll.getStatus()))
            throw new BadRequestException("Payroll cannot be archived while its payslip email is being sent.");

        List<SuiteRecord> affected = relatedPayslip(payroll);
        if (affected.stream().anyMatch(row -> "EMAIL_SENDING".equals(row.getStatus())))
            throw new BadRequestException("Payroll cannot be archived while its payslip email is being sent.");

        LocalDateTime now = LocalDateTime.now();
        affected.add(payroll);
        affected.forEach(row -> {
            row.setArchivedAt(now);
            row.setArchivedBy(actorId);
        });
        repository.saveAll(affected);
        affected.forEach(row -> events.save(SuiteRecordArchiveEvent.builder()
                .recordId(row.getId()).action("ARCHIVED").actorId(actorId).build()));
        return payroll;
    }

    @Transactional
    public SuiteRecord restore(UUID id, UUID actorId) {
        SuiteRecord payroll = repository.lockByIdAndModule(id, "payroll")
                .orElseThrow(() -> new ResourceNotFoundException("Payroll record", id));
        if (payroll.getArchivedAt() == null) throw new BadRequestException("This payroll record is not archived.");

        List<SuiteRecord> affected = relatedPayslip(payroll);
        payroll.setArchivedAt(null);
        payroll.setArchivedBy(null);
        affected.add(payroll);
        affected.forEach(row -> {
            row.setArchivedAt(null);
            row.setArchivedBy(null);
        });
        repository.saveAll(affected);
        affected.forEach(row -> events.save(SuiteRecordArchiveEvent.builder()
                .recordId(row.getId()).action("RESTORED").actorId(actorId).build()));
        return payroll;
    }

    private List<SuiteRecord> relatedPayslip(SuiteRecord payroll) {
        List<SuiteRecord> records = new ArrayList<>();
        repository.lockByModuleAndEmployeeIdAndPeriod("payslips", payroll.getEmployeeId(), payroll.getPeriod())
                .ifPresent(records::add);
        return records;
    }
}

