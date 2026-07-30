package com.technnext.hrms.email;

/**
 * Raised by EmployeeInviteService right after Send Invitation succeeds.
 * Mirrors EmployeeWelcomeEmailEvent's design (plain snapshot, no JPA entities,
 * consumed by an AFTER_COMMIT + @Async listener).
 *
 * @param toEmail       the candidate's email address
 * @param firstName     for the greeting
 * @param lastName      for the greeting
 * @param employeeCode  shown for reference
 * @param onboardingUrl the full link, e.g. https://hrms.technnext.com/employee/onboarding?token=...
 */
public record EmployeeInviteEmailEvent(
        String toEmail,
        String firstName,
        String lastName,
        String employeeCode,
        String onboardingUrl
) {
}