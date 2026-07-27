package com.technnext.hrms.attendance.dto;

import java.util.UUID;

// status must be APPROVED or REJECTED
public record RegularizationDecision(
        UUID reviewedBy,
        String status,
        String reviewerRemarks
) {}