package com.technnext.hrms.invite.dto;

import com.technnext.hrms.employee.dto.EducationDto;
import com.technnext.hrms.employee.dto.ExperienceDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the CANDIDATE fills in on the public onboarding page — i.e.
 * everything NOT already fixed by the admin's Invite Employee form (which set
 * name/dept/designation/manager/DOJ/role, all read-only here).
 */
public record OnboardingCompleteRequest(
        // Personal
        LocalDate dateOfBirth,
        String gender,
        String bloodGroup,
        String maritalStatus,
        String nationality,

        // Address (current)
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,

        // Address (permanent) — collected separately since employee_contacts
        // distinguishes current vs. permanent address.
        String permAddressLine1,
        String permAddressLine2,
        String permCity,
        String permState,
        String permPincode,

        // Emergency contact
        String emergencyContactName,
        String emergencyContactPhone,
        String emergencyContactRelation,

        // Government IDs / bank
        String aadhaarNumber,
        String panNumber,
        String bankAccountNumber,
        String bankName,
        String ifscCode,
        String uanNumber,

        Boolean isFresher,
        List<EducationDto> education,
        List<ExperienceDto> experience,

        // Uploaded documents — each item's docType is one of:
        // PROFILE_PHOTO, RESUME, AADHAAR, PAN, DEGREE_CERTIFICATE,
        // EXPERIENCE_CERTIFICATE, BANK_PASSBOOK, OTHER.
        // The server (not the client) decides which existing document
        // category + display label each maps to.
        List<OnboardingDocumentDto> documents,

        // Password the candidate is choosing for themselves
        String password,
        String confirmPassword
) {
}