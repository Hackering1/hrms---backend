package com.technnext.hrms.invite.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The small "Send Invitation" form — deliberately minimal, matching the spec:
 * Employee Code (auto), First Name, Last Name, Department, Designation,
 * Reporting Manager, Date of Joining, Login Email, Create Login As.
 * No Aadhaar/PAN/Education/Experience/Bank/Documents/Password — the candidate
 * fills those themselves via the onboarding link.
 */
public record InviteEmployeeRequest(
        String employeeCode,   // optional — auto-generated if blank, same as the classic form
        String firstName,
        String lastName,
        Integer departmentId,
        Integer designationId,
        Integer branchId,       // optional; not in the spec's field list but harmless to allow
        Integer shiftId,        // optional; ditto
        UUID managerId,
        LocalDate dateOfJoining,
        String email,
        String loginRole
) {
}