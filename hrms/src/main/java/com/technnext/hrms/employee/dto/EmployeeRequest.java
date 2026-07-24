package com.technnext.hrms.employee.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EmployeeRequest(
        String employeeCode,
        String firstName,
        String lastName,
        String middleName,
        LocalDate dateOfBirth,
        String gender,
        String bloodGroup,
        String maritalStatus,
        String nationality,
        LocalDate dateOfJoining,
        String employmentType,
        String status,
        Integer branchId,
        Integer departmentId,
        Integer designationId,
        Integer shiftId,
        LocalDate probationEndDate,
        LocalDate confirmationDate,
        // personal documents + fresher flag
        Boolean isFresher,
        String aadhaarNumber,
        String panNumber,
        String bankAccountNumber,
        String bankName,
        String ifscCode,
        String uanNumber,
        String email,        // for auto-created login
        String loginRole,     // EMPLOYEE or MANAGER (null = no login)
        String password,      // initial login password (default User@0412 if blank)
        // nested lists (the "+" add rows)
        List<EducationDto> education,
        List<ExperienceDto> experience,
        // Manager Portal: who this new hire reports to. When a MANAGER creates the
        // employee this is ignored and forced to the creating manager (server-side);
        // a SUPER_ADMIN may set it explicitly from the Manager Portal. null = unassigned.
        UUID managerId
) {}