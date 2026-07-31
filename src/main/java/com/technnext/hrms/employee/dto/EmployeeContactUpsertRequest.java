package com.technnext.hrms.employee.dto;

public record EmployeeContactUpsertRequest(
        String personalEmail,
        String officialEmail,
        String phonePrimary,
        String phoneSecondary,
        String emergencyName,
        String emergencyPhone,
        String emergencyRelation,
        // current address
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String country,
        String pincode,
        // permanent address
        String permAddressLine1,
        String permAddressLine2,
        String permCity,
        String permState,
        String permPincode
) {
}