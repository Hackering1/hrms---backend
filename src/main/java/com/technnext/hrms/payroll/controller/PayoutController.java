package com.technnext.hrms.payroll.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.payroll.dto.PayoutBatchResponse;
import com.technnext.hrms.payroll.entity.PayoutBatch;
import com.technnext.hrms.payroll.entity.PayoutTransaction;
import com.technnext.hrms.payroll.service.PayoutService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Initiates and tracks salary payout via Cashfree Payouts for an APPROVED
 * payroll run. SUPER_ADMIN + HR_ADMIN only — see PayoutService's class-level
 * comment for the safety gates this endpoint relies on (approval-only,
 * one-batch-per-run, deterministic transfer ids, amount reconciliation).
 */
@RestController
@RequestMapping("/api/payroll/runs/{runId}/payout")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
public class PayoutController {

    private final PayoutService payoutService;
    private final EmployeeRepository employeeRepository;

    @PostMapping
    public ApiResponse<PayoutBatchResponse> initiate(@PathVariable Integer runId, @AuthenticationPrincipal CustomUserDetails principal) {
        PayoutService.InitiateResult result = payoutService.initiate(runId, principal.getUser().getId());

        Map<UUID, Employee> employeesById = employeeRepository.findAllById(
                result.transactions().stream().map(PayoutTransaction::getEmployeeId).toList()
        ).stream().collect(Collectors.toMap(Employee::getId, e -> e));

        List<PayoutBatchResponse.TransactionRow> txRows = result.transactions().stream()
                .map(tx -> toTransactionRow(tx, employeesById.get(tx.getEmployeeId())))
                .toList();
        List<PayoutBatchResponse.SkippedRow> skippedRows = result.skipped().stream()
                .map(s -> new PayoutBatchResponse.SkippedRow(s.employeeId(), s.employeeCode(), s.reason()))
                .toList();

        String message = result.skipped().isEmpty()
                ? "Payout initiated for " + result.transactions().size() + " employee(s)."
                : "Payout initiated for " + result.transactions().size() + " employee(s) — "
                  + result.skipped().size() + " skipped (see details).";

        return ApiResponse.ok(message, toResponse(result.batch(), txRows, skippedRows));
    }

    @GetMapping
    public ApiResponse<PayoutBatchResponse> status(@PathVariable Integer runId) {
        PayoutBatch batch = payoutService.getBatch(runId);
        List<PayoutTransaction> transactions = payoutService.getTransactions(batch.getId());
        Map<UUID, Employee> employeesById = employeeRepository.findAllById(
                transactions.stream().map(PayoutTransaction::getEmployeeId).toList()
        ).stream().collect(Collectors.toMap(Employee::getId, e -> e));

        List<PayoutBatchResponse.TransactionRow> txRows = transactions.stream()
                .map(tx -> toTransactionRow(tx, employeesById.get(tx.getEmployeeId())))
                .toList();

        return ApiResponse.ok(toResponse(batch, txRows, List.of()));
    }

    @PostMapping("/refresh")
    public ApiResponse<PayoutBatchResponse> refresh(@PathVariable Integer runId) {
        PayoutBatch batch = payoutService.refreshStatus(runId);
        List<PayoutTransaction> transactions = payoutService.getTransactions(batch.getId());
        Map<UUID, Employee> employeesById = employeeRepository.findAllById(
                transactions.stream().map(PayoutTransaction::getEmployeeId).toList()
        ).stream().collect(Collectors.toMap(Employee::getId, e -> e));

        List<PayoutBatchResponse.TransactionRow> txRows = transactions.stream()
                .map(tx -> toTransactionRow(tx, employeesById.get(tx.getEmployeeId())))
                .toList();

        return ApiResponse.ok("Status refreshed", toResponse(batch, txRows, List.of()));
    }

    private PayoutBatchResponse.TransactionRow toTransactionRow(PayoutTransaction tx, Employee e) {
        return new PayoutBatchResponse.TransactionRow(
                tx.getEmployeeId(),
                e != null ? e.getEmployeeCode() : null,
                e != null ? (e.getFirstName() + " " + e.getLastName()) : null,
                tx.getTransferId(),
                tx.getStatus(),
                tx.getStatusDescription(),
                tx.getAmount(),
                tx.getUtr()
        );
    }

    private PayoutBatchResponse toResponse(PayoutBatch batch, List<PayoutBatchResponse.TransactionRow> transactions, List<PayoutBatchResponse.SkippedRow> skipped) {
        return new PayoutBatchResponse(
                batch.getId(), batch.getPayrollRunId(), batch.getBatchTransferId(), batch.getCfBatchTransferId(),
                batch.getStatus(), batch.getTotalAmount(), batch.getEmployeeCount(), batch.getEnvironment(),
                batch.getInitiatedAt(), batch.getLastStatusCheckAt(), transactions, skipped
        );
    }
}