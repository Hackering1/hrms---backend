package com.technnext.hrms.leave.dto;

import java.util.UUID;

// status must be APPROVED or REJECTED
public record LeaveApproval(
        UUID approvedBy,
        String status,
        String approverRemarks
) {}