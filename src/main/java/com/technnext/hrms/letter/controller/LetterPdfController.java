package com.technnext.hrms.letter.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.email.EmailService;
import com.technnext.hrms.employee.dto.EmployeeContactDto;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.employee.service.EmployeeContactService;
import com.technnext.hrms.letter.dto.LetterEmailRequest;
import com.technnext.hrms.letter.dto.LetterPdfRequest;
import com.technnext.hrms.letter.service.LetterPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
    // NEW — reused as-is (existing Microsoft Graph mailer, same infra used
    // for welcome/invite emails). No second email system is created.
    private final EmailService emailService;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ResponseEntity<byte[]> generate(@RequestBody LetterPdfRequest request) {
        LetterPdfRequest enriched = enrich(request);
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

    /**
     * NEW — generates the exact same letter PDF as POST /api/letter-pdf
     * (same enrich() + pdfService.generate() call, nothing duplicated) and
     * emails it via the existing EmailService/Graph mailer instead of
     * returning it for download. Same role restriction as generate() above,
     * so this can never become reachable by an unauthenticated or
     * unauthorized caller.
     *
     * Order matches the required flow: validate email -> generate PDF ->
     * only then attempt to send. A PDF-generation failure never reaches the
     * email step; an email failure never claims the PDF wasn't produced —
     * the two are reported distinctly to the frontend.
     */
    @PostMapping("/send-email")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ResponseEntity<ApiResponse<String>> sendEmail(@RequestBody LetterEmailRequest request) {
        String email = request.recipientEmail() == null ? "" : request.recipientEmail().trim();
        if (email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BadRequestException("Please enter a valid email address.");
        }
        if (request.letter() == null) {
            throw new BadRequestException("Letter details are required.");
        }

        LetterPdfRequest enriched = enrich(request.letter());

        byte[] pdf;
        try {
            pdf = pdfService.generate(enriched);
        } catch (Exception ex) {
            // Never attempt to send on a failed/incomplete document.
            throw new BadRequestException("Couldn't generate the letter — please check the entered details.");
        }

        String label = typeLabel(enriched.letterType()).replace('_', ' ');
        String name = enriched.employeeName() == null || enriched.employeeName().isBlank()
                ? "Candidate" : enriched.employeeName();
        String filename = typeLabel(enriched.letterType()) + "_Letter_" +
                name.replaceAll("\\s+", "_") + ".pdf";

        // NEW — dynamic subject/body for Offer, Contract Offer (C2H), and
        // Appointment letters only (the 3 types explicitly asked for). Every
        // other letter type (Relieving/Experience/Internship) keeps the
        // original "Label - Name" subject and plain body exactly as before —
        // untouched, not just unaffected by coincidence.
        String role = enriched.designation() == null || enriched.designation().isBlank()
                ? null : enriched.designation().trim();
        // Reuses the exact same gender-detection pattern LetterPdfService
        // already applies for pronouns in the PDF itself (startsWith "M"/"F",
        // case-insensitive) — same field, same convention, no new logic.
        String g = enriched.gender() == null ? "" : enriched.gender().trim().toUpperCase();
        String title = g.startsWith("M") ? "Mr. " : g.startsWith("F") ? "Ms. " : "";
        EmailCopy copy = emailCopyFor(enriched.letterType(), label, name, role, title);

        String subject = copy.subject();
        String htmlBody = copy.body();

        try {
            emailService.sendLetterEmail(email, subject, htmlBody, pdf, filename);
        } catch (Exception ex) {
            // Never leak SMTP/Graph/internal error details to the client.
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Failed to send offer letter. Please try again."));
        }

        return ResponseEntity.ok(
                ApiResponse.ok("Offer letter sent successfully to " + email + ".", email));
    }

    // NEW — the one place the company's legal name is used for email text.
    // Deliberately NOT a refactor of LetterPdfService's own (separately)
    // hardcoded copies of this same string in the PDF body — this constant
    // only feeds the email subject/body built in this controller.
    private static final String COMPANY_NAME = "TechNext Technologies and Services Private Limited";

    private record EmailCopy(String subject, String body) {}

    /**
     * Builds the subject + HTML body for Offer/C2H/Appointment emails.
     * Subject:
     *   Offer:        "Offer of Employment – {role} | {company}"
     *   C2H:          "Contract Offer – {role} | {company}"
     *   Appointment:  "Appointment Letter – {role} | {company}"
     * Body follows the exact wording requested (greeting + warm closing,
     * candidate name/role/letter-type dynamically inserted, no signatory
     * name — signs off simply as "HR" per spec).
     * Any other type falls back to the original, unchanged "Label - Name"
     * subject and plain body — this method only branches for the 3 types
     * above; everything else returns the pre-existing copy untouched.
     */
    private EmailCopy emailCopyFor(String letterType, String label, String name, String role, String title) {
        String type = letterType == null ? "" : letterType.toUpperCase();
        String subjectPrefix;
        String typePhrase; // lowercase, used inline in body sentences
        switch (type) {
            case "OFFER":
                subjectPrefix = "Offer of Employment";
                typePhrase = "offer letter";
                break;
            case "C2H":
                subjectPrefix = "Contract Offer";
                typePhrase = "contract offer letter";
                break;
            case "APPOINTMENT":
                subjectPrefix = "Appointment Letter";
                typePhrase = "appointment letter";
                break;
            default:
                // Relieving / Experience / Internship / anything else —
                // original behavior, byte-for-byte.
                String oldSubject = label + " - " + name;
                String oldBody = "<p>Dear " + escapeHtml(name) + ",</p>"
                        + "<p>Please find attached your " + escapeHtml(label) + ".</p>"
                        + "<p>Regards,<br/>HR Team<br/>TechNext Technologies and Services Pvt Ltd</p>";
                return new EmailCopy(oldSubject, oldBody);
        }

        String subject = role == null
                ? subjectPrefix + " | " + COMPANY_NAME
                : subjectPrefix + " \u2013 " + role + " | " + COMPANY_NAME;

        String shareLine = role == null
                ? "We are pleased to share with you the " + escapeHtml(typePhrase)
                        + " with our organization. The " + escapeHtml(typePhrase)
                        + " is attached herewith for your review."
                : "We are pleased to share with you the " + escapeHtml(typePhrase)
                        + " for the position of " + escapeHtml(role) + " with our organization. The "
                        + escapeHtml(typePhrase) + " is attached herewith for your review.";

        String body = "<p>Dear " + escapeHtml(title) + escapeHtml(name) + ",</p>"
                + "<p>Greetings from " + escapeHtml(COMPANY_NAME) + "!</p>"
                + "<p>" + shareLine + "</p>"
                + "<p>Kindly go through the terms and conditions and confirm your acceptance by signing "
                + "and sharing a scanned copy of the document within 24 hours of receiving this email.</p>"
                + "<p>Should you have any questions or require any further clarification, please feel free "
                + "to reach out to us.</p>"
                + "<p>We look forward to welcoming you to the TechNext team and wish you a successful "
                + "journey with us.</p>"
                + "<p>Warm regards,<br/>Thanks,<br/>HR</p>";

        return new EmailCopy(subject, body);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
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

    /**
     * Extracted, unchanged, from the original generate() body — enriches
     * gender/address from the employee record when the frontend didn't
     * already supply them. Used by both /api/letter-pdf and
     * /api/letter-pdf/send-email so the enrichment logic exists in exactly
     * one place.
     */
    private LetterPdfRequest enrich(LetterPdfRequest request) {
        LetterPdfRequest enriched = request;

        String gender = request.gender();
        if ((gender == null || gender.isBlank()) && request.employeeId() != null) {
            gender = employeeRepository.findById(request.employeeId())
                    .map(Employee::getGender)
                    .orElse(gender);
        }

        String address = request.employeeAddress();
        if ((address == null || address.isBlank()) && request.employeeId() != null) {
            String looked = formatAddress(employeeContactService.getByEmployeeId(request.employeeId()));
            if (looked != null) address = looked;
        }

        if (!java.util.Objects.equals(gender, request.gender())
                || !java.util.Objects.equals(address, request.employeeAddress())) {
            enriched = withEnrichment(request, gender, address);
        }
        return enriched;
    }

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