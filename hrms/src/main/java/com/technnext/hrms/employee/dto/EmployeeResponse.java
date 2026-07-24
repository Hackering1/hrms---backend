package com.technnext.hrms.employee.dto;
import com.technnext.hrms.employee.entity.Employee;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record EmployeeResponse(
        UUID id,
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
        String branchName,
        Integer departmentId,
        String departmentName,
        Integer designationId,
        String designationName,
        Integer shiftId,
        String shiftName,
        LocalDate probationEndDate,
        LocalDate confirmationDate,
        // personal documents + fresher
        Boolean isFresher,
        String aadhaarNumber,
        String panNumber,
        String bankAccountNumber,
        String bankName,
        String ifscCode,
        String uanNumber,
        String email,
        UUID userId,           // linked login account id (for reliable role lookup)
        String tempPassword,   // only populated right after auto-creating a login
        String profilePhotoUrl,
        // reporting manager (resolved from employee_managers)
        UUID reportingManagerId,
        String reportingManagerName,
        // nested
        List<EducationDto> education,
        List<ExperienceDto> experience
) {
    /** Base mapping without nested lists or manager (all default to empty/null). */
    public static EmployeeResponse from(Employee e) {
        return from(e, List.of(), List.of(), null, null);
    }

    /** Mapping with nested lists but no manager info. */
    public static EmployeeResponse from(Employee e, List<EducationDto> edu, List<ExperienceDto> exp) {
        return from(e, edu, exp, null, null);
    }

    /** Full mapping including education + experience + reporting manager. */
    public static EmployeeResponse from(Employee e, List<EducationDto> edu, List<ExperienceDto> exp,
                                        UUID reportingManagerId, String reportingManagerName) {
        return new EmployeeResponse(
                e.getId(), e.getEmployeeCode(), e.getFirstName(), e.getLastName(), e.getMiddleName(),
                e.getDateOfBirth(), e.getGender(), e.getBloodGroup(), e.getMaritalStatus(), e.getNationality(),
                e.getDateOfJoining(), e.getEmploymentType(), e.getStatus(),
                e.getBranch() != null ? e.getBranch().getId() : null,
                e.getBranch() != null ? e.getBranch().getName() : null,
                e.getDepartment() != null ? e.getDepartment().getId() : null,
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getDesignation() != null ? e.getDesignation().getId() : null,
                e.getDesignation() != null ? e.getDesignation().getName() : null,
                e.getShift() != null ? e.getShift().getId() : null,
                e.getShift() != null ? e.getShift().getName() : null,
                e.getProbationEndDate(), e.getConfirmationDate(),
                e.getIsFresher(), e.getAadhaarNumber(), e.getPanNumber(),
                e.getBankAccountNumber(), e.getBankName(), e.getIfscCode(), e.getUanNumber(),
                e.getEmail(), e.getUserId(), null, e.getProfilePhotoUrl(),
                reportingManagerId, reportingManagerName,
                edu, exp
        );
    }
}