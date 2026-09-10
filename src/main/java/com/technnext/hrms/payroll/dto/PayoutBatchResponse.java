package com.technnext.hrms.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PayoutBatchResponse(
        Integer id,
        Integer payrollRunId,
        String batchTransferId,
        String cfBatchTransferId,
        String status,
        BigDecimal totalAmount,
        Integer employeeCount,
        String environment,
        LocalDateTime initiatedAt,
        LocalDateTime lastStatusCheckAt,
        List<TransactionRow> transactions,
        List<SkippedRow> skipped
) {
    public record TransactionRow(
            UUID employeeId,
            String employeeCode,
            String employeeName,
            String transferId,
            String status,
            String statusDescription,
            BigDecimal amount,
            String utr
    ) {}

    public record SkippedRow(UUID employeeId, String employeeCode, String reason) {}
}