package com.technnext.hrms.leave.service;

import com.technnext.hrms.leave.dto.LeaveBalanceCreate;
import com.technnext.hrms.leave.entity.LeaveBalance;
import com.technnext.hrms.leave.entity.LeaveType;
import com.technnext.hrms.leave.repository.LeaveBalanceRepository;
import com.technnext.hrms.leave.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final LeaveBalanceRepository repository;
    private final LeaveTypeRepository leaveTypeRepository;

    @Transactional(readOnly = true)
    public List<LeaveBalance> getByEmployeeAndYear(UUID employeeId, Integer year) {
        return repository.findByEmployeeIdAndYear(employeeId, year);
    }

    // Allocate (or update) a single leave balance row for an employee/type/year.
    @Transactional
    public LeaveBalance allocate(LeaveBalanceCreate req) {
        LeaveBalance bal = repository
                .findByEmployeeIdAndLeaveTypeIdAndYear(req.employeeId(), req.leaveTypeId(), req.year())
                .orElseGet(() -> LeaveBalance.builder()
                        .employeeId(req.employeeId())
                        .leaveTypeId(req.leaveTypeId())
                        .year(req.year())
                        .usedDays(BigDecimal.ZERO)
                        .pendingDays(BigDecimal.ZERO)
                        .build());
        bal.setAllocatedDays(req.allocatedDays() != null ? req.allocatedDays() : BigDecimal.ZERO);
        bal.setCarriedDays(req.carriedDays() != null ? req.carriedDays() : BigDecimal.ZERO);
        if (bal.getUsedDays() == null) bal.setUsedDays(BigDecimal.ZERO);
        if (bal.getPendingDays() == null) bal.setPendingDays(BigDecimal.ZERO);
        return repository.save(bal);
    }

    /**
     * Zoho-style prorated accrual based on the employee's join date.
     * The joining month counts as a FULL month, e.g. join in May (month 5) -> 8/12 of the annual quota.
     * Result is rounded to the nearest whole day. Runs for every active leave type.
     * Gender-specific leave types are skipped if they don't apply to the employee.
     *
     * Idempotent: if a balance row already exists for the year, its allocated value is refreshed
     * (used/pending are preserved).
     */
    @Transactional
    public void accrueForNewEmployee(UUID employeeId, LocalDate dateOfJoining, String gender) {
        if (dateOfJoining == null) dateOfJoining = LocalDate.now();
        int year = dateOfJoining.getYear();
        int joinMonth = dateOfJoining.getMonthValue();          // May -> 5
        int monthsEligible = 12 - joinMonth + 1;                // May -> 8 (join month counts)

        List<LeaveType> types = leaveTypeRepository.findAll();
        for (LeaveType lt : types) {
            if (Boolean.FALSE.equals(lt.getIsActive())) continue;

            // Respect gender rules (ALL / MALE / FEMALE)
            String applicable = lt.getApplicableGender() == null ? "ALL" : lt.getApplicableGender();
            if (!"ALL".equalsIgnoreCase(applicable)
                    && gender != null
                    && !applicable.equalsIgnoreCase(gender)) {
                continue;
            }

            BigDecimal annual = lt.getDaysPerYear() == null ? BigDecimal.ZERO : lt.getDaysPerYear();
            // prorated = annual * monthsEligible / 12, rounded to nearest whole day
            BigDecimal prorated = annual
                    .multiply(BigDecimal.valueOf(monthsEligible))
                    .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP);

            LeaveBalance bal = repository
                    .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, lt.getId(), year)
                    .orElseGet(() -> LeaveBalance.builder()
                            .employeeId(employeeId)
                            .leaveTypeId(lt.getId())
                            .year(year)
                            .usedDays(BigDecimal.ZERO)
                            .pendingDays(BigDecimal.ZERO)
                            .carriedDays(BigDecimal.ZERO)
                            .build());
            bal.setAllocatedDays(prorated);
            if (bal.getUsedDays() == null) bal.setUsedDays(BigDecimal.ZERO);
            if (bal.getPendingDays() == null) bal.setPendingDays(BigDecimal.ZERO);
            if (bal.getCarriedDays() == null) bal.setCarriedDays(BigDecimal.ZERO);
            repository.save(bal);
        }
    }
}