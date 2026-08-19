package com.technnext.hrms.letter.controller;

import com.technnext.hrms.employee.dto.EmployeeContactDto;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.employee.service.EmployeeContactService;
import com.technnext.hrms.letter.dto.LetterPdfRequest;
import com.technnext.hrms.letter.service.LetterPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a formatted Offer/Appointment/Relieving/Experience letter PDF and
 * returns it for download. HR enters salary lines in the request body; the PDF
 * is built server-side. The employee's gender and current address are looked
 * up from the record (gender for his/her wording on relieving/experience
 * letters; address for the "To," block on Offer/Appointment/C2H letters) so
 * the frontend doesn't need to send — or the caller doesn't need direct
 * access to — that data itself. This endpoint is already restricted to
 * SUPER_ADMIN/HR_ADMIN/HR_EXECUTIVE below, which is the correct (broader)
 * scope for letter generation — deliberately looked up via the repository/
 * service directly rather than through the self/team-scoped
 * GET /api/employees/{id}/contact endpoint, which would 403 for HR staff
 * generating a letter for a candidate outside their own reporting line.
 */
@RestController
@RequestMapping("/api/letter-pdf")
@RequiredArgsConstructor
public class LetterPdfController {

    private final LetterPdfService pdfService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeContactService employeeContactService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ResponseEntity<byte[]> generate(@RequestBody LetterPdfRequest request) {
        LetterPdfRequest enriched = request;

        // Enrich gender from the employee record (frontend doesn't need to send it).
        String gender = request.gender();
        if ((gender == null || gender.isBlank()) && request.employeeId() != null) {
            gender = employeeRepository.findById(request.employeeId())
                    .map(Employee::getGender)
                    .orElse(gender);
        }

        // Enrich current address from EmployeeContact (frontend doesn't need
        // to send it, and typed-but-unmatched candidates simply have none).
        String address = request.employeeAddress();
        if ((address == null || address.isBlank()) && request.employeeId() != null) {
            String looked = formatAddress(employeeContactService.getByEmployeeId(request.employeeId()));
            if (looked != null) address = looked;
        }

        if (!java.util.Objects.equals(gender, request.gender())
                || !java.util.Objects.equals(address, request.employeeAddress())) {
            enriched = withEnrichment(request, gender, address);
        }

        byte[] pdf = pdfService.generate(enriched);
        String filename = typeLabel(enriched.letterType()) + "_Letter_" +
                (enriched.employeeName() == null ? "Employee"
                        : enriched.employeeName().replaceAll("\\s+", "_")) + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    private String typeLabel(String type) {
        if (type == null) return "Offer";
        switch (type.toUpperCase()) {
            case "APPOINTMENT": return "Appointment";
            case "RELIEVING":   return "Relieving";
            case "EXPERIENCE":  return "Experience";
            case "INTERNSHIP":  return "Internship";
            case "C2H":         return "Contract_to_Hire_Offer";
            default:            return "Offer";
        }
    }

    /**
     * Builds the multi-line "To," block address from EmployeeContact's
     * current-address fields — each present field becomes its own line,
     * blank fields are simply skipped, and nothing is invented or hardcoded.
     * City + pincode are combined on one line (e.g. "Belgavi-590003") to
     * match standard Indian address formatting; state, if present, is added
     * to that same line.
     */
    private String formatAddress(EmployeeContactDto c) {
        if (c == null) return null;
        List<String> lines = new ArrayList<>();
        if (notBlank(c.addressLine1())) lines.add(c.addressLine1().trim());
        if (notBlank(c.addressLine2())) lines.add(c.addressLine2().trim());

        StringBuilder cityLine = new StringBuilder();
        if (notBlank(c.city())) cityLine.append(c.city().trim());
        if (notBlank(c.state())) {
            if (cityLine.length() > 0) cityLine.append(", ");
            cityLine.append(c.state().trim());
        }
        if (notBlank(c.pincode())) {
            cityLine.append(cityLine.length() > 0 ? "-" : "").append(c.pincode().trim());
        }
        if (cityLine.length() > 0) lines.add(cityLine.toString());

        if (notBlank(c.country())) lines.add(c.country().trim());

        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }

    /** Rebuilds the request with gender/address filled in (records are immutable). */
    private LetterPdfRequest withEnrichment(LetterPdfRequest r, String gender, String address) {
        return new LetterPdfRequest(
                r.employeeId(), r.employeeName(), gender, address, r.letterType(),
                r.letterDate(), r.place(), r.dateOfJoining(), r.employmentEndDate(),
                r.internshipDetails(),
                r.designation(), r.workLocation(),
                r.employmentType(), r.contractDuration(), r.contractDurationUnit(),
                r.ctcAnnual(), r.ctcInWords(),
                r.basicM(), r.basicA(), r.hraM(), r.hraA(), r.ltaM(), r.ltaA(),
                r.specialM(), r.specialA(), r.grossM(), r.grossA(),
                r.pfEmployerM(), r.pfEmployerA(), r.insuranceM(), r.insuranceA(),
                r.gratuityM(), r.gratuityA(),
                r.variablePayM(), r.variablePayA(),
                r.employerCostM(), r.employerCostA(),
                r.ctcMonthlyTotal(), r.ctcAnnualTotal(),
                r.pfEmployeeM(), r.pfEmployeeA(), r.ptM(), r.ptA(),
                r.deductionsM(), r.deductionsA(), r.netM(), r.netA(),
                r.signatoryName(), r.signatoryTitle(), r.signatureFileId());
    }
}