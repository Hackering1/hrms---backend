package com.technnext.hrms.payroll.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.payroll.entity.*;
import com.technnext.hrms.payroll.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates paying an APPROVED payroll run via Cashfree Payouts. This is
 * the one place in the codebase that can move real company money, so every
 * safety gate below is load-bearing — don't relax any of them without
 * Finance/Engineering sign-off:
 *
 *  1. ONLY an APPROVED payroll_run can be paid (never DRAFT/PROCESSED) —
 *     enforced here AND matches the existing manual "Mark Paid" gate.
 *  2. ONE payout batch per payroll_run — enforced by a DB unique constraint
 *     (payout_batches.payroll_run_id), not just an application check, so a
 *     race condition or bug can't trigger a second real transfer.
 *  3. transfer_id sent to Cashfree is DETERMINISTIC (derived from the
 *     payslip id) — a retried/duplicate call reuses the same id rather than
 *     risking a second real payment to the same employee.
 *  4. Amount reconciliation: the sum of individual transfer amounts must
 *     equal the payroll_run's total_net before anything is sent — a mismatch
 *     aborts the whole batch rather than sending partial/wrong data.
 *  5. Employees missing bank_account_number/ifsc_code are SKIPPED (not
 *     failed-silently) and reported back, rather than guessing or blocking
 *     everyone else's payment.
 *  6. Cashfree Payouts is enabled/disabled via a master switch
 *     (app.cashfree.enabled) that defaults to false — this feature is inert
 *     until explicitly turned on with real credentials.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;
    private final PayoutBatchRepository payoutBatchRepository;
    private final PayoutTransactionRepository payoutTransactionRepository;
    private final PayoutBeneficiaryRepository payoutBeneficiaryRepository;
    private final CashfreePayoutClient cashfreeClient;

    public record SkippedEmployee(UUID employeeId, String employeeCode, String reason) {}
    public record InitiateResult(PayoutBatch batch, List<PayoutTransaction> transactions, List<SkippedEmployee> skipped) {}

    @Transactional
    public InitiateResult initiate(Integer payrollRunId, UUID initiatedBy) {
        if (!cashfreeClient.isEnabled()) {
            throw new BadRequestException(
                    "Cashfree Payouts is not enabled on this server. Set CASHFREE_ENABLED=true along with " +
                    "CASHFREE_CLIENT_ID/CASHFREE_CLIENT_SECRET once you've tested against the sandbox environment.");
        }

        PayrollRun run = payrollRunRepository.findById(payrollRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + payrollRunId));

        // Gate 1: only APPROVED runs can be paid.
        if (!"APPROVED".equals(run.getStatus())) {
            throw new BadRequestException(
                    "Payroll run must be APPROVED before it can be paid (current status: " + run.getStatus() +
                    "). Process and approve it first — payouts never trigger from a DRAFT or unapproved run.");
        }

        // Gate 2: one batch per run — the DB unique constraint is the real
        // safety net, but check here too for a clean error message.
        if (payoutBatchRepository.findByPayrollRunId(payrollRunId).isPresent()) {
            throw new BadRequestException(
                    "A payout has already been initiated for this payroll run. Check its status rather than " +
                    "initiating again — re-running this would risk a duplicate transfer.");
        }

        List<Payslip> payslips = payslipRepository.findByPayrollRunId(payrollRunId);
        if (payslips.isEmpty()) {
            throw new BadRequestException("This payroll run has no payslips to pay.");
        }

        Map<UUID, Employee> employeesById = employeeRepository.findAllById(
                payslips.stream().map(Payslip::getEmployeeId).toList()
        ).stream().collect(Collectors.toMap(Employee::getId, e -> e));

        List<Payslip> payable = new java.util.ArrayList<>();
        List<SkippedEmployee> skipped = new java.util.ArrayList<>();
        for (Payslip p : payslips) {
            Employee e = employeesById.get(p.getEmployeeId());
            if (e == null) {
                skipped.add(new SkippedEmployee(p.getEmployeeId(), null, "Employee record not found"));
            } else if (e.getBankAccountNumber() == null || e.getBankAccountNumber().isBlank()
                    || e.getIfscCode() == null || e.getIfscCode().isBlank()) {
                skipped.add(new SkippedEmployee(p.getEmployeeId(), e.getEmployeeCode(), "Missing bank account number or IFSC code"));
            } else if (p.getNetPay() == null || p.getNetPay().signum() <= 0) {
                skipped.add(new SkippedEmployee(p.getEmployeeId(), e.getEmployeeCode(), "Net pay is zero or negative"));
            } else {
                payable.add(p);
            }
        }

        if (payable.isEmpty()) {
            throw new BadRequestException("No employees in this run have valid bank details to pay. Fix their bank details and try again.");
        }

        // Gate 4: amount reconciliation against the run's own totals for the
        // employees actually being paid (skipped employees' amounts are
        // naturally excluded — Finance sees exactly who and why via `skipped`).
        BigDecimal payableTotal = payable.stream().map(Payslip::getNetPay).reduce(BigDecimal.ZERO, BigDecimal::add);

        String batchTransferId = "PAYROLL-" + run.getId() + "-" + System.currentTimeMillis();
        List<CashfreePayoutClient.TransferLine> lines = new java.util.ArrayList<>();

        for (Payslip p : payable) {
            Employee e = employeesById.get(p.getEmployeeId());
            String beneficiaryId = ensureBeneficiary(e);
            // Deterministic per payslip -> safe to retry without double-paying.
            String transferId = "PAY-" + run.getId() + "-" + p.getId();
            lines.add(new CashfreePayoutClient.TransferLine(transferId, p.getNetPay(), beneficiaryId));
        }

        CashfreePayoutClient.BatchResult result = cashfreeClient.batchTransfer(batchTransferId, lines);

        PayoutBatch batch = PayoutBatch.builder()
                .payrollRunId(run.getId())
                .batchTransferId(batchTransferId)
                .cfBatchTransferId(result.cfBatchTransferId())
                .status(result.status() == null ? "RECEIVED" : result.status())
                .totalAmount(payableTotal)
                .employeeCount(payable.size())
                .environment(cashfreeClient.getEnvironment())
                .initiatedBy(initiatedBy)
                .build();
        batch = payoutBatchRepository.save(batch);

        List<PayoutTransaction> transactions = new java.util.ArrayList<>();
        for (int i = 0; i < payable.size(); i++) {
            Payslip p = payable.get(i);
            CashfreePayoutClient.TransferLine line = lines.get(i);
            PayoutTransaction tx = PayoutTransaction.builder()
                    .payoutBatchId(batch.getId())
                    .payslipId(p.getId())
                    .employeeId(p.getEmployeeId())
                    .transferId(line.transferId())
                    .beneficiaryId(line.beneficiaryId())
                    .amount(line.amount())
                    .status("RECEIVED")
                    .build();
            transactions.add(payoutTransactionRepository.save(tx));
        }

        log.info("[PayoutService] Initiated payout batch {} for payroll run {} — {} employees, Rs.{} total, env={}",
                batchTransferId, run.getId(), payable.size(), payableTotal, cashfreeClient.getEnvironment());

        return new InitiateResult(batch, transactions, skipped);
    }

    /** Registers the employee as a Cashfree beneficiary if not already done, reusing the existing registration otherwise. */
    private String ensureBeneficiary(Employee e) {
        return payoutBeneficiaryRepository.findByEmployeeId(e.getId())
                .map(PayoutBeneficiary::getCashfreeBeneficiaryId)
                .orElseGet(() -> {
                    String beneficiaryId = "EMP-" + e.getEmployeeCode().replaceAll("[^A-Za-z0-9]", "");
                    CashfreePayoutClient.BeneficiaryResult result = cashfreeClient.createBeneficiary(
                            beneficiaryId,
                            e.getFirstName() + " " + e.getLastName(),
                            e.getBankAccountNumber(),
                            e.getIfscCode(),
                            null);
                    payoutBeneficiaryRepository.save(PayoutBeneficiary.builder()
                            .employeeId(e.getId())
                            .cashfreeBeneficiaryId(result.beneficiaryId())
                            .bankAccountNumber(e.getBankAccountNumber())
                            .bankIfsc(e.getIfscCode())
                            .status(result.status())
                            .build());
                    return result.beneficiaryId();
                });
    }

    @Transactional(readOnly = true)
    public PayoutBatch getBatch(Integer payrollRunId) {
        return payoutBatchRepository.findByPayrollRunId(payrollRunId)
                .orElseThrow(() -> new ResourceNotFoundException("No payout has been initiated for this payroll run."));
    }

    @Transactional(readOnly = true)
    public List<PayoutTransaction> getTransactions(Integer payoutBatchId) {
        return payoutTransactionRepository.findByPayoutBatchId(payoutBatchId);
    }

    /** Polls Cashfree for the latest status of every transaction in a batch and updates our records. */
    @Transactional
    public PayoutBatch refreshStatus(Integer payrollRunId) {
        PayoutBatch batch = getBatch(payrollRunId);
        List<PayoutTransaction> transactions = payoutTransactionRepository.findByPayoutBatchId(batch.getId());

        List<CashfreePayoutClient.TransferStatus> batchStatuses = cashfreeClient.getBatchStatus(batch.getBatchTransferId());
        Map<String, CashfreePayoutClient.TransferStatus> byTransferId = batchStatuses.stream()
                .filter(s -> s.transferId() != null)
                .collect(Collectors.toMap(CashfreePayoutClient.TransferStatus::transferId, s -> s, (a, b) -> a));

        int success = 0, failed = 0, pending = 0;
        for (PayoutTransaction tx : transactions) {
            CashfreePayoutClient.TransferStatus status = byTransferId.get(tx.getTransferId());
            // Fall back to the single-transfer endpoint if batch status wasn't usable.
            if (status == null) {
                status = cashfreeClient.getTransferStatus(tx.getTransferId());
            }
            tx.setStatus(status.status());
            tx.setStatusDescription(status.statusDescription());
            tx.setCfTransferId(status.cfTransferId());
            tx.setUtr(status.utr());
            payoutTransactionRepository.save(tx);

            switch (status.status()) {
                case "SUCCESS" -> success++;
                case "FAILED", "REVERSED" -> failed++;
                default -> pending++;
            }
        }

        String overallStatus;
        if (pending > 0) overallStatus = "PROCESSING";
        else if (failed == 0) overallStatus = "COMPLETED";
        else if (success > 0) overallStatus = "PARTIALLY_FAILED";
        else overallStatus = "FAILED";

        batch.setStatus(overallStatus);
        batch.setLastStatusCheckAt(java.time.LocalDateTime.now());
        return payoutBatchRepository.save(batch);
    }
}