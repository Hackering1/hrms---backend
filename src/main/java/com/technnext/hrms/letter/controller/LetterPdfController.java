package com.technnext.hrms.letter.controller;

import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.letter.dto.LetterPdfRequest;
import com.technnext.hrms.letter.service.LetterPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Generates a formatted Offer/Appointment/Relieving/Experience letter PDF and
 * returns it for download. HR enters salary lines in the request body; the PDF
 * is built server-side. The employee's gender is looked up from the record so
 * relieving/experience letters use correct pronouns (his/her).
 */
@RestController
@RequestMapping("/api/letter-pdf")
@RequiredArgsConstructor
public class LetterPdfController {

    private final LetterPdfService pdfService;
    private final EmployeeRepository employeeRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ResponseEntity<byte[]> generate(@RequestBody LetterPdfRequest request) {
        // Enrich gender from the employee record (frontend doesn't need to send it).
        LetterPdfRequest enriched = request;
        if ((request.gender() == null || request.gender().isBlank()) && request.employeeId() != null) {
            String gender = employeeRepository.findById(request.employeeId())
                    .map(Employee::getGender)
                    .orElse(null);
            if (gender != null) {
                enriched = withGender(request, gender);
            }
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

    /** Rebuilds the request with gender set (records are immutable). */
    private LetterPdfRequest withGender(LetterPdfRequest r, String gender) {
        return new LetterPdfRequest(
                r.employeeId(), r.employeeName(), gender, r.letterType(),
                r.letterDate(), r.place(), r.dateOfJoining(), r.employmentEndDate(),
                r.internshipDetails(),
                r.designation(), r.workLocation(),
                r.employmentType(), r.contractDuration(), r.contractDurationUnit(),
                r.ctcAnnual(), r.ctcInWords(),
                r.basicM(), r.basicA(), r.hraM(), r.hraA(), r.ltaM(), r.ltaA(),
                r.specialM(), r.specialA(), r.grossM(), r.grossA(),
                r.pfEmployerM(), r.pfEmployerA(), r.insuranceM(), r.insuranceA(),
                r.gratuityM(), r.gratuityA(), r.employerCostM(), r.employerCostA(),
                r.ctcMonthlyTotal(), r.ctcAnnualTotal(),
                r.pfEmployeeM(), r.pfEmployeeA(), r.ptM(), r.ptA(),
                r.deductionsM(), r.deductionsA(), r.netM(), r.netA(),
                r.signatoryName(), r.signatoryTitle(), r.signatureFileId());
    }
}