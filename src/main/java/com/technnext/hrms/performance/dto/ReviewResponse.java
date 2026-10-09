package com.technnext.hrms.performance.dto;

import com.technnext.hrms.performance.entity.PerformanceReview;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * status is NOT_STARTED when the employee hasn't submitted a self review yet (id is null then).
 * The manager's rating/comments are only exposed to the employee once the review is COMPLETED.
 */
public record ReviewResponse(
        Integer id,
        Integer cycleId,
        UUID employeeId,
        String status,
        Integer selfRating,
        String selfComments,
        LocalDateTime selfSubmittedAt,
        Integer managerRating,
        String managerComments,
        UUID reviewerId,
        LocalDateTime completedAt
) {
    public static ReviewResponse from(PerformanceReview r, boolean hideManagerUnlessCompleted) {
        boolean hide = hideManagerUnlessCompleted && !"COMPLETED".equals(r.getStatus());
        return new ReviewResponse(r.getId(), r.getCycleId(), r.getEmployeeId(), r.getStatus(),
                r.getSelfRating(), r.getSelfComments(), r.getSelfSubmittedAt(),
                hide ? null : r.getManagerRating(), hide ? null : r.getManagerComments(),
                hide ? null : r.getReviewerId(), hide ? null : r.getCompletedAt());
    }

    public static ReviewResponse notStarted(Integer cycleId, UUID employeeId) {
        return new ReviewResponse(null, cycleId, employeeId, "NOT_STARTED",
                null, null, null, null, null, null, null);
    }
}