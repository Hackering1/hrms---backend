package com.technnext.hrms.employee.dto;

import java.time.LocalDate;

/** Fields a user is allowed to edit on their own profile (self-service). */
public record ProfileUpdateRequest(
        String firstName,
        String lastName,
        String middleName,
        LocalDate dateOfBirth,
        String gender,
        String bloodGroup,
        String maritalStatus,
        String nationality
) {}