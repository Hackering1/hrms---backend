package com.technnext.hrms.hrops.dto;

import java.util.UUID;

// status: CONFIRMED, EXTENDED, or TERMINATED
public record ProbationReview(
        String status,
        String reviewNotes,
        UUID reviewedBy
) {}