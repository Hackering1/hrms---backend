package com.technnext.hrms.timesheet.dto;

/** status: APPROVED or REJECTED. Remarks are mandatory when rejecting. */
public record TimesheetDecision(String status, String remarks) {}