package com.technnext.hrms.employee.dto;

import com.technnext.hrms.employee.entity.EmployeeContact;
import java.util.UUID;

/**
 * The single source of truth for an employee's address and contact details —
 * current address, permanent address, emergency contact, phone/email.
 *
 * Deliberately separate from EmployeeResponse's core identity/employment
 * fields: contact details change independently of job details, and keeping
 * them in their own table (employee_contacts) avoids re-adding an "address"
 * duplicate onto the employees table.
 */
public record EmployeeContactDto(
        UUID employeeId,
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
    public static EmployeeContactDto from(EmployeeContact c) {
        if (c == null) return null;
        return new EmployeeContactDto(
                c.getEmployeeId(), c.getPersonalEmail(), c.getOfficialEmail(),
                c.getPhonePrimary(), c.getPhoneSecondary(),
                c.getEmergencyName(), c.getEmergencyPhone(), c.getEmergencyRelation(),
                c.getAddressLine1(), c.getAddressLine2(), c.getCity(), c.getState(),
                c.getCountry(), c.getPincode(),
                c.getPermAddressLine1(), c.getPermAddressLine2(), c.getPermCity(),
                c.getPermState(), c.getPermPincode()
        );
    }
}