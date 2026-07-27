package com.technnext.hrms.leave.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.leave.dto.LeaveApproval;
import com.technnext.hrms.leave.dto.LeaveRegularizeRequest;
import com.technnext.hrms.leave.dto.LeaveRequestCreate;
import com.technnext.hrms.leave.dto.LeaveRequestResponse;
import com.technnext.hrms.leave.entity.LeaveBalance;
import com.technnext.hrms.leave.entity.LeaveRequest;
import com.technnext.hrms.leave.repository.LeaveBalanceRepository;
import com.technnext.hrms.leave.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository requestRepository;
    private final LeaveBalanceRepository balanceRepository;
    private final LeaveDaysCalculator daysCalculator;

    public List<LeaveRequestResponse> getAll() {
        return requestRepository.findAll().stream().map(LeaveRequestResponse::from).toList();
    }

    public List<LeaveRequestResponse> getByEmployee(UUID employeeId) {
        return requestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream().map(LeaveRequestResponse::from).toList();
    }

    public List<LeaveRequestResponse> getPending() {
        return requestRepository.findByStatusOrderByCreatedAtDesc("PENDING")
                .stream().map(LeaveRequestResponse::from).toList();
    }

    public LeaveRequestResponse getById(Integer id) {
        return LeaveRequestResponse.from(findOrThrow(id));
    }

    /**
     * Apply for leave.
     *
     * CORPORATE CORRECTNESS:
     *  - numberOfDays is COMPUTED ON THE SERVER from the date range, excluding
     *    weekends and public holidays (via LeaveDaysCalculator). The value sent by
     *    the browser is ignored.
     *  - to-date >= from-date.
     *  - the range must contain at least one working day.
     *  - no overlap with an existing PENDING/APPROVED leave.
     *  - sufficient balance for the computed number of days.
     *
     * NOTE: past dates are permitted, so HR can record a BACK-DATED leave
     * (leave regularization, meaning 1) simply by applying with past dates.
     */
    @Transactional
    public LeaveRequestResponse apply(LeaveRequestCreate req) {
        if (req.employeeId() == null) {
            throw new BadRequestException("employeeId is required");
        }
        if (req.fromDate() == null || req.toDate() == null) {
            throw new BadRequestException("fromDate and toDate are required");
        }
        if (req.toDate().isBefore(req.fromDate())) {
            throw new BadRequestException("To-date cannot be before from-date");
        }

        BigDecimal days = daysCalculator.calculate(req.fromDate(), req.toDate(), req.dayType());
        if (days.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(
                    "The selected dates fall entirely on weekends/holidays — no working days to apply for.");
        }

        boolean overlaps = requestRepository
                .findByEmployeeIdOrderByCreatedAtDesc(req.employeeId())
                .stream()
                .filter(r -> "PENDING".equals(r.getStatus()) || "APPROVED".equals(r.getStatus()))
                .anyMatch(r -> !req.fromDate().isAfter(r.getToDate())
                        && !r.getFromDate().isAfter(req.toDate()));
        if (overlaps) {
            throw new BadRequestException(
                    "You already have a leave request overlapping these dates. "
                    + "Cancel it first, or pick different dates.");
        }

        LeaveBalance bal = currentYearBalance(req.employeeId(), req.leaveTypeId());
        if (bal != null) {
            BigDecimal available = nz(bal.getAllocatedDays())
                    .add(nz(bal.getCarriedDays()))
                    .subtract(nz(bal.getUsedDays()))
                    .subtract(nz(bal.getPendingDays()));
            if (days.compareTo(available) > 0) {
                throw new BadRequestException(
                    "Insufficient leave balance. This request needs " + days
                    + " day(s) but only " + available + " are available.");
            }
        }

        LeaveRequest r = LeaveRequest.builder()
                .employeeId(req.employeeId())
                .leaveTypeId(req.leaveTypeId())
                .fromDate(req.fromDate())
                .toDate(req.toDate())
                .numberOfDays(days)
                .dayType(req.dayType() != null ? req.dayType() : "FULL")
                .reason(req.reason())
                .status("PENDING")
                .documentUrl(req.documentUrl())
                .build();
        r = requestRepository.save(r);

        if (bal != null) {
            bal.setPendingDays(nz(bal.getPendingDays()).add(days));
            balanceRepository.save(bal);
        }
        return LeaveRequestResponse.from(r);
    }

    /**
     * Approve or reject. Uses the stored (server-computed) numberOfDays for the
     * balance transition, with a max(0) guard against negative pending balances.
     */
    @Transactional
    public LeaveRequestResponse decide(Integer id, LeaveApproval approval) {
        LeaveRequest r = findOrThrow(id);
        if (!"PENDING".equals(r.getStatus())) {
            throw new BadRequestException("Only PENDING requests can be decided");
        }
        String status = approval.status();
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new BadRequestException("status must be APPROVED or REJECTED");
        }
        if ("REJECTED".equals(status)
                && (approval.approverRemarks() == null || approval.approverRemarks().isBlank())) {
            throw new BadRequestException("Please provide a remark when rejecting a leave request.");
        }

        r.setStatus(status);
        r.setApprovedBy(approval.approvedBy());
        r.setApprovedAt(LocalDateTime.now());
        r.setApproverRemarks(approval.approverRemarks());
        requestRepository.save(r);

        LeaveBalance bal = currentYearBalance(r.getEmployeeId(), r.getLeaveTypeId());
        if (bal != null) {
            BigDecimal days = r.getNumberOfDays();
            bal.setPendingDays(nz(bal.getPendingDays()).subtract(days).max(BigDecimal.ZERO));
            if ("APPROVED".equals(status)) {
                bal.setUsedDays(nz(bal.getUsedDays()).add(days));
            }
            balanceRepository.save(bal);
        }
        return LeaveRequestResponse.from(r);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HR LEAVE REGULARIZATION (edit / cancel done directly by HR)
    //
    // Corporate rules:
    //   - CANCEL: days go back to the employee's balance (pending days if the
    //     leave was PENDING, used days if APPROVED). Never below zero.
    //   - EDIT: reverse the old effect on the balance, then apply the new one.
    //     If the new day count exceeds what's available, the edit is BLOCKED
    //     (no negative balances). A leave-type change moves days between the two
    //     types' balances.
    //   - Day counts are recomputed on the server (weekends/holidays excluded).
    // Access is enforced at the controller (HR/Admin, and managers for their team).
    // ─────────────────────────────────────────────────────────────────────────

    /** HR cancels a leave and returns the days to the employee's balance. */
    @Transactional
    public LeaveRequestResponse cancel(Integer id) {
        LeaveRequest r = findOrThrow(id);
        if ("CANCELLED".equals(r.getStatus())) {
            throw new BadRequestException("This leave is already cancelled.");
        }

        LeaveBalance bal = currentYearBalance(r.getEmployeeId(), r.getLeaveTypeId());
        if (bal != null) {
            BigDecimal days = nz(r.getNumberOfDays());
            if ("APPROVED".equals(r.getStatus())) {
                bal.setUsedDays(nz(bal.getUsedDays()).subtract(days).max(BigDecimal.ZERO));
            } else if ("PENDING".equals(r.getStatus())) {
                bal.setPendingDays(nz(bal.getPendingDays()).subtract(days).max(BigDecimal.ZERO));
            }
            balanceRepository.save(bal);
        }

        r.setStatus("CANCELLED");
        requestRepository.save(r);
        return LeaveRequestResponse.from(r);
    }

    /**
     * HR/Admin permanently removes a leave request row (typically old test
     * data or a CANCELLED entry that's just clutter). If it wasn't already
     * CANCELLED/REJECTED, its balance effect is reversed first so a
     * PENDING/APPROVED leave doesn't silently leave days stuck against the
     * employee's balance after the row disappears.
     */
    @Transactional
    public void deletePermanent(Integer id) {
        LeaveRequest r = findOrThrow(id);
        if ("PENDING".equals(r.getStatus()) || "APPROVED".equals(r.getStatus())) {
            LeaveBalance bal = currentYearBalance(r.getEmployeeId(), r.getLeaveTypeId());
            if (bal != null) {
                BigDecimal days = nz(r.getNumberOfDays());
                if ("APPROVED".equals(r.getStatus())) {
                    bal.setUsedDays(nz(bal.getUsedDays()).subtract(days).max(BigDecimal.ZERO));
                } else {
                    bal.setPendingDays(nz(bal.getPendingDays()).subtract(days).max(BigDecimal.ZERO));
                }
                balanceRepository.save(bal);
            }
        }
        requestRepository.delete(r);
    }

    /**
     * HR edits an existing leave. Recomputes days and adjusts balances by
     * reversing the old entry and applying the new one (blocking if insufficient).
     */
    @Transactional
    public LeaveRequestResponse regularize(Integer id, LeaveRegularizeRequest req) {
        LeaveRequest r = findOrThrow(id);
        if ("CANCELLED".equals(r.getStatus())) {
            throw new BadRequestException("Cannot edit a cancelled leave. Create a new one instead.");
        }
        if (req.fromDate() == null || req.toDate() == null) {
            throw new BadRequestException("fromDate and toDate are required");
        }
        if (req.toDate().isBefore(req.fromDate())) {
            throw new BadRequestException("To-date cannot be before from-date");
        }

        Integer newTypeId = req.leaveTypeId() != null ? req.leaveTypeId() : r.getLeaveTypeId();
        BigDecimal newDays = daysCalculator.calculate(req.fromDate(), req.toDate(), req.dayType());
        if (newDays.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(
                    "The selected dates fall entirely on weekends/holidays — no working days.");
        }

        boolean approved = "APPROVED".equals(r.getStatus());
        BigDecimal oldDays = nz(r.getNumberOfDays());

        // 1) reverse the OLD entry from the OLD type's balance
        LeaveBalance oldBal = currentYearBalance(r.getEmployeeId(), r.getLeaveTypeId());
        if (oldBal != null) {
            if (approved) {
                oldBal.setUsedDays(nz(oldBal.getUsedDays()).subtract(oldDays).max(BigDecimal.ZERO));
            } else {
                oldBal.setPendingDays(nz(oldBal.getPendingDays()).subtract(oldDays).max(BigDecimal.ZERO));
            }
            balanceRepository.save(oldBal);
        }

        // 2) apply the NEW entry to the NEW type's balance, blocking if insufficient
        LeaveBalance newBal = currentYearBalance(r.getEmployeeId(), newTypeId);
        if (newBal != null) {
            BigDecimal available = nz(newBal.getAllocatedDays())
                    .add(nz(newBal.getCarriedDays()))
                    .subtract(nz(newBal.getUsedDays()))
                    .subtract(nz(newBal.getPendingDays()));
            if (newDays.compareTo(available) > 0) {
                // roll back the reversal so nothing is left inconsistent
                if (oldBal != null) {
                    if (approved) oldBal.setUsedDays(nz(oldBal.getUsedDays()).add(oldDays));
                    else oldBal.setPendingDays(nz(oldBal.getPendingDays()).add(oldDays));
                    balanceRepository.save(oldBal);
                }
                throw new BadRequestException(
                        "Not enough balance for this change. Available: " + available + " day(s).");
            }
            if (approved) newBal.setUsedDays(nz(newBal.getUsedDays()).add(newDays));
            else newBal.setPendingDays(nz(newBal.getPendingDays()).add(newDays));
            balanceRepository.save(newBal);
        }

        // 3) update the request itself
        r.setLeaveTypeId(newTypeId);
        r.setFromDate(req.fromDate());
        r.setToDate(req.toDate());
        r.setNumberOfDays(newDays);
        r.setDayType(req.dayType() != null ? req.dayType() : "FULL");
        if (req.reason() != null && !req.reason().isBlank()) r.setReason(req.reason());
        requestRepository.save(r);

        return LeaveRequestResponse.from(r);
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private LeaveBalance currentYearBalance(UUID employeeId, Integer leaveTypeId) {
        int year = java.time.Year.now().getValue();
        return balanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveTypeId, year)
                .orElse(null);
    }

    private LeaveRequest findOrThrow(Integer id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", id));
    }
}
