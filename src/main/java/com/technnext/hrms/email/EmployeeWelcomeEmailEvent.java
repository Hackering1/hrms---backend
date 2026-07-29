package com.technnext.hrms.email;

/**
 * Raised by EmployeeService right after a new employee (and, optionally, their
 * login account) has been saved. Carries a plain snapshot of the data the
 * welcome email needs — never the Employee entity itself, so the listener
 * (which runs on a separate thread, after the DB transaction has committed)
 * never touches a detached/lazy-loaded JPA object.
 *
 * @param toEmail          the candidate's email address (required — the event is only
 *                         published when this is present)
 * @param firstName        employee's first name, for the greeting
 * @param lastName         employee's last name
 * @param employeeCode     the Employee ID (e.g. "TN0001") — always included
 * @param designationName  may be null
 * @param departmentName   may be null
 * @param loginEmail       the login username, if a login account was created (else null)
 * @param tempPassword     the temporary password, if a login account was created (else null)
 */
public record EmployeeWelcomeEmailEvent(
        String toEmail,
        String firstName,
        String lastName,
        String employeeCode,
        String designationName,
        String departmentName,
        String loginEmail,
        String tempPassword
) {
}