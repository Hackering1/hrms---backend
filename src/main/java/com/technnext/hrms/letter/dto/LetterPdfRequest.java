package com.technnext.hrms.letter.dto;

import java.util.UUID;

/**
 * Request to generate a formatted Offer / Appointment / Relieving letter PDF.
 * HR enters the values (salary lines, dates); strings are used for amounts so HR
 * can type pre-formatted values like "16,500".
 */
public record LetterPdfRequest(
        UUID employeeId,
        String employeeName,      // full name, resolved from employee record
        String gender,            // "MALE" | "FEMALE" | null — drives his/her wording
        // NEW — current address (used for the "To," block below the
        // candidate's name in Offer/Appointment/C2H letters). Server-enriched
        // from EmployeeContact when employeeId is set and the frontend didn't
        // already supply one — same pattern as gender below.
        String employeeAddress,
        String letterType,        // "OFFER" | "APPOINTMENT" | "RELIEVING" | "EXPERIENCE" | "INTERNSHIP"
        String letterDate,        // e.g. "08 July 2026"
        String place,             // e.g. "Bangalore"
        String dateOfJoining,     // e.g. "02 June 2025" (relieving/internship: START date)
        String employmentEndDate, // relieving/experience/internship: END date, e.g. "06 July 2026"
        // NEW — INTERNSHIP only: HR-typed free text describing the intern's
        // responsibilities/technologies/contributions. Never hardcoded —
        // blank/omitted simply skips that paragraph in the letter.
        String internshipDetails,
        String designation,       // e.g. "Software Developer"
        String workLocation,      // e.g. "Bangalore"
        // NEW — employment type drives the wording in clause 1 of the Offer/
        // Appointment letter. "FULL_TIME" | "PART_TIME" | "CONTRACT".
        // For CONTRACT, contractDuration + contractDurationUnit ("DAYS"|"MONTHS")
        // describe the length of the engagement (e.g. "6" + "MONTHS").
        String employmentType,
        String contractDuration,
        String contractDurationUnit,
        String ctcAnnual,         // e.g. "5,00,004"
        String ctcInWords,        // e.g. "Rupees Five Lakh..."
        // salary lines (monthly, annual)
        String basicM, String basicA,
        String hraM, String hraA,
        String ltaM, String ltaA,
        String specialM, String specialA,
        String grossM, String grossA,
        String pfEmployerM, String pfEmployerA,
        String insuranceM, String insuranceA,
        String gratuityM, String gratuityA,
        // NEW — optional Variable Pay component. Folded into the "Employer
        // Costs Included in CTC" section of the salary annexure, right above
        // Total Employer Cost — blank/"0" (i.e. omitted from the PDF row)
        // whenever the frontend's Variable Pay toggle is "No".
        String variablePayM, String variablePayA,
        String employerCostM, String employerCostA,
        String ctcMonthlyTotal, String ctcAnnualTotal,
        String pfEmployeeM, String pfEmployeeA,
        String ptM, String ptA,
        String deductionsM, String deductionsA,
        String netM, String netA,
        // HR signatory (signature left blank for manual signing)
        String signatoryName,     // e.g. "M Gouse Modeen"
        String signatoryTitle,    // e.g. "Director-Human Resources"
        // #14: optional uploaded HR Director signature. Accepts a stored-file id
        // or a "/api/files/{id}" url; when present it is embedded in the letter.
        String signatureFileId
) {}