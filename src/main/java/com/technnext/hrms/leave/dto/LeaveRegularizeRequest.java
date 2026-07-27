package com.technnext.hrms.leave.dto;

import java.time.LocalDate;

/**
 * HR edits (regularizes) an existing leave record. Any field may change; the day
 * count is recomputed on the server and the balance is adjusted by the difference.
 */
public record LeaveRegularizeRequest(
        Integer leaveTypeId,   // may move the leave to a different type
        LocalDate fromDate,
        LocalDate toDate,
        String dayType,        // "FULL" | "HALF_DAY"
        String reason
) {}