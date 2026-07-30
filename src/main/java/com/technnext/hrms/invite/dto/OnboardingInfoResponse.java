package com.technnext.hrms.invite.dto;

public record OnboardingInfoResponse(
        String employeeCode,
        String firstName,
        String lastName,
        String email,
        String departmentName,
        String designationName,
        String managerName,
        String dateOfJoining,   // ISO string, e.g. "2026-08-01"
        String loginRole,
        long minutesRemaining   // until the token expires, for a "time left" banner
) {
}