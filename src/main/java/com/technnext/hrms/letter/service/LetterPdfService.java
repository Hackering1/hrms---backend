package com.technnext.hrms.letter.service;
import com.lowagie.text.*;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.ColumnText;
import com.technnext.hrms.letter.dto.LetterPdfRequest;
import com.technnext.hrms.file.service.FileStorageService;
import com.technnext.hrms.file.entity.StoredFile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
/**
 * Generates formatted Offer / Appointment / Relieving letter PDFs (letterhead,
 * clauses, salary table) using OpenPDF. HR-entered values are merged in.
 *
 * Fonts: whole document uses an EMBEDDED Times New Roman-metric-compatible
 *        Unicode font ("Tinos" — see src/main/resources/fonts/NOTICE.md),
 *        not OpenPDF's built-in Font.TIMES_ROMAN. The built-in one is Adobe's
 *        standard-14 "Times-Roman" — visually close but not the same font,
 *        never embedded (every PDF viewer substitutes its own local
 *        lookalike, which is why the same PDF could look subtly different
 *        font-to-font across viewers), and limited to a narrow legacy
 *        character set that silently drops anything outside it — including
 *        the ₹ symbol used throughout the salary table. Embedding Tinos
 *        fixes both: identical rendering everywhere, and full Unicode
 *        coverage (₹, en-dash, bullet, etc.).
 * Signature: embeds src/main/resources/signature.png above the signatory name
 *            if present; otherwise leaves blank space for manual signing.
 */
@Service
public class LetterPdfService {
    // #14: used to load an uploaded HR Director signature by stored-file id.
    private final FileStorageService fileStorageService;

    public LetterPdfService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    private static final Color BLUE = new Color(0x1F, 0x6F, 0xC0);
    private static final Color CYAN = new Color(0x00, 0xD4, 0xF0);   // brand cyan (footer rule)
    private static final Color LIGHTBLUE = new Color(0xD5, 0xE8, 0xF0);
    private static final Color GREY = new Color(0x33, 0x33, 0x33);

    // Embedded Times New Roman-metric-compatible font (see class doc above).
    // IDENTITY_H + full byte[] embedding = full Unicode glyph coverage,
    // baked into the PDF itself rather than relying on the reader's fonts.
    private static final BaseFont TIMES_REGULAR = loadEmbeddedFont("Tinos-Regular.ttf");
    private static final BaseFont TIMES_BOLD = loadEmbeddedFont("Tinos-Bold.ttf");

    private static BaseFont loadEmbeddedFont(String fileName) {
        try (InputStream is = new ClassPathResource("fonts/" + fileName).getInputStream()) {
            byte[] fontBytes = is.readAllBytes();
            return BaseFont.createFont(fileName, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
        } catch (IOException | DocumentException e) {
            // A missing/corrupt font resource means every letter would fail
            // to generate anyway — fail fast at class-load time with a clear
            // cause rather than a confusing NPE deep inside PDF generation.
            throw new ExceptionInInitializerError("Failed to load embedded font " + fileName + ": " + e.getMessage());
        }
    }

    // All fonts: embedded Tinos (Times New Roman-metric-compatible), regular
    // weight from TIMES_REGULAR, bold weight from TIMES_BOLD — never
    // Font.BOLD synthetic bolding on top of the regular face, since a real
    // bold outline (from the actual bold font file) renders more faithfully
    // than a synthesized one.
    private static final Font H_LOGO = new Font(TIMES_BOLD, 18, Font.NORMAL, BLUE);
    private static final Font H_SMALL = new Font(TIMES_REGULAR, 7, Font.NORMAL, GREY);
    private static final Font CONTACT = new Font(TIMES_REGULAR, 12, Font.NORMAL, Color.BLACK);
    // Main letter heading ("OFFER LETTER" / "APPOINTMENT LETTER" / "EMPLOYMENT
    // SERVICE LETTER" / "EXPERIENCE CUM RELIEVING LETTER").
    private static final Font TITLE = new Font(TIMES_BOLD, 16, Font.NORMAL, Color.BLACK);
    // Annexure-A heading specifically — deliberately a different size (14pt) per
    // spec, so it needed its own constant rather than sharing TITLE.
    private static final Font ANNEXURE_TITLE = new Font(TIMES_BOLD, 14, Font.NORMAL, Color.BLACK);
    private static final Font SUBTITLE = new Font(TIMES_BOLD, 13, Font.NORMAL, Color.BLACK);
    private static final Font CLAUSE_T = new Font(TIMES_BOLD, 13, Font.NORMAL, Color.BLACK);
    private static final Font BODY = new Font(TIMES_REGULAR, 12, Font.NORMAL, Color.BLACK);
    private static final Font BODY_B = new Font(TIMES_BOLD, 12, Font.NORMAL, Color.BLACK);
    private static final Font CELL = new Font(TIMES_REGULAR, 9, Font.NORMAL, Color.BLACK);
    private static final Font CELL_B = new Font(TIMES_BOLD, 9, Font.NORMAL, Color.BLACK);
    private static final Font CELL_W = new Font(TIMES_BOLD, 9, Font.NORMAL, Color.WHITE);
    private static final Font FOOT = new Font(TIMES_REGULAR, 12, Font.NORMAL, GREY);

    /**
     * The fonts the shared helpers (letterhead contact lines, date block, title,
     * clauses, signatory, acceptance, footer) draw with. {@link #STANDARD_FONTS}
     * is exactly the constants above, so every existing letter renders as before.
     * Only the Internship Offer Letter passes a different set (see
     * {@link #internshipOfferFonts()}) — same sizes, same colours, different
     * typeface only.
     */
    private record LetterFonts(Font body, Font bodyB, Font clauseT, Font title, Font contact, Font foot) {}

    private static final LetterFonts STANDARD_FONTS =
            new LetterFonts(BODY, BODY_B, CLAUSE_T, TITLE, CONTACT, FOOT);

    /**
     * Internship Offer Letter fonts: genuine Times New Roman when its files can be
     * found (see {@link TimesNewRomanFonts}); otherwise the standard Tinos set,
     * with a WARNING logged on every generation. Sizes mirror the constants above
     * exactly (body 12, clause heading 13, title 16, contact 12, footer 12).
     */
    private static LetterFonts internshipOfferFonts() {
        TimesNewRomanFonts.Resolved tnr = TimesNewRomanFonts.resolve(TIMES_REGULAR, TIMES_BOLD);
        return new LetterFonts(
                new Font(tnr.regular(), 12, Font.NORMAL, Color.BLACK),
                new Font(tnr.bold(), 12, Font.NORMAL, Color.BLACK),
                new Font(tnr.bold(), 13, Font.NORMAL, Color.BLACK),
                new Font(tnr.bold(), 16, Font.NORMAL, Color.BLACK),
                new Font(tnr.regular(), 12, Font.NORMAL, Color.BLACK),
                new Font(tnr.regular(), 12, Font.NORMAL, GREY));
    }
    public byte[] generate(LetterPdfRequest r) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 50, 50, 45, 88);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            // Watermark behind every page + address/CIN footer on every page.
            // Internship Offer Letter only: footer text uses the same Times New
            // Roman font set as the rest of that letter (sizes/positions identical).
            boolean internshipOffer = "INTERNSHIP_OFFER".equalsIgnoreCase(r.letterType());
            LetterFonts internshipFonts = internshipOffer ? internshipOfferFonts() : null;
            writer.setPageEvent(internshipOffer
                    ? new PageDecorator(internshipFonts.foot())
                    : new PageDecorator());
            doc.open();
            String type = r.letterType() == null ? "OFFER" : r.letterType().toUpperCase();
            // Relieving / Experience letter is a distinct, single-page layout.
            if (type.equals("RELIEVING") || type.equals("EXPERIENCE") || type.equals("SERVICE")) {
                addLetterhead(doc);
                addRelievingBody(doc, r);
                doc.close();
                return out.toByteArray();
            }
            // Internship Experience Letter — also a distinct single-page
            // layout (no resignation/relieving language, no salary annexure),
            // reusing the same letterhead/watermark/footer/signature helpers.
            if (type.equals("INTERNSHIP")) {
                addLetterhead(doc);
                addInternshipBody(doc, r);
                doc.close();
                return out.toByteArray();
            }
            // Internship Offer Letter — separate from the existing INTERNSHIP
            // experience-letter path above. It uses the supplied offer-letter
            // clause structure and conditionally renders Paid/Unpaid content.
            if (type.equals("INTERNSHIP_OFFER")) {
                addInternshipOfferLetter(doc, r, writer, internshipFonts);
                doc.close();
                return out.toByteArray();
            }
            // Contract-to-Hire Offer Letter — new letter type. Distinct clause
            // set (contract term, nature of employment, termination-with-notice,
            // etc.) per the company's C2H reference document, but reuses the
            // same letterhead/date/title/signatory/employee-acceptance/salary
            // annexure helpers as the standard Offer Letter — no existing
            // OFFER/APPOINTMENT code path is touched.
            if (type.equals("C2H")) {
                addLetterhead(doc);
                addDateBlock(doc, r);
                addTitle(doc, "OFFER LETTER (Contract-to-Hire)");
                addC2HRecipient(doc, r);
                addC2HBody(doc, r, writer);
                addSalaryAnnexure(doc, r, false);
                doc.close();
                return out.toByteArray();
            }
            boolean appointment = type.equals("APPOINTMENT");
            addLetterhead(doc);
            addDateBlock(doc, r);
            addTitle(doc, appointment ? "APPOINTMENT LETTER" : "OFFER LETTER");
            addRecipient(doc, r, appointment);
            if (appointment) addAppointmentBody(doc, r);
            else addOfferBody(doc, r, writer);
            addSalaryAnnexure(doc, r, appointment);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate letter PDF: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Relieving / Experience ("Employment Service Letter")
    // ─────────────────────────────────────────────────────────────────────────
    private void addRelievingBody(Document doc, LetterPdfRequest r) throws DocumentException {
        String type = r.letterType() == null ? "RELIEVING" : r.letterType().toUpperCase();
        boolean experience = type.equals("EXPERIENCE");

        // Gender-aware pronouns (fall back to "their/them" if unknown).
        String g = r.gender() == null ? "" : r.gender().trim().toUpperCase();
        boolean male = g.startsWith("M");
        boolean female = g.startsWith("F");
        String his = male ? "his" : female ? "her" : "their";
        String he = male ? "he" : female ? "she" : "they";
        String him = male ? "him" : female ? "her" : "them";

        Paragraph gap0 = new Paragraph(" ", BODY);
        gap0.setSpacingAfter(18f);
        doc.add(gap0);

        Paragraph title = new Paragraph(
                experience ? "EXPERIENCE CUM RELIEVING LETTER" : "EMPLOYMENT SERVICE LETTER", TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10f);
        doc.add(title);

        Paragraph sub = new Paragraph("TO WHOMSOEVER IT MAY CONCERN", SUBTITLE);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(18f);
        doc.add(sub);

        doc.add(new Paragraph("Date: " + safe(r.letterDate()), BODY));
        Paragraph gap1 = new Paragraph(" ", BODY);
        gap1.setSpacingAfter(6f);
        doc.add(gap1);

        String start = safe(r.dateOfJoining());
        String end = safe(r.employmentEndDate());
        Paragraph line1 = new Paragraph();
        line1.setAlignment(Element.ALIGN_JUSTIFIED);
        line1.add(new Chunk("This is to certify that ", BODY));
        line1.add(new Chunk(safe(r.employeeName()), BODY_B));
        line1.add(new Chunk(
                " was employed with TechNext Technologies and Services Private Limited from ", BODY));
        line1.add(new Chunk(start, BODY_B));
        line1.add(new Chunk(" to ", BODY));
        line1.add(new Chunk(end, BODY_B));
        line1.add(new Chunk(" as ", BODY));
        line1.add(new Chunk(safe(r.designation()), BODY_B));
        line1.add(new Chunk(".", BODY));
        doc.add(line1);

        if (experience) {
            // Combined Experience & Relieving letter: experience praise + relieving.
            Paragraph l2 = new Paragraph();
            l2.setAlignment(Element.ALIGN_JUSTIFIED);
            l2.setSpacingBefore(6f);
            l2.add(new Chunk("During " + his + " tenure with us, " + he
                    + " was found to be sincere, hardworking, and professional in "
                    + his + " conduct. " + cap(he) + " performed " + his
                    + " assigned duties and responsibilities to our satisfaction.", BODY));
            doc.add(l2);

            Paragraph l3 = new Paragraph();
            l3.setAlignment(Element.ALIGN_JUSTIFIED);
            l3.setSpacingBefore(6f);
            l3.add(new Chunk("With reference to " + his + " resignation, " + he
                    + " has been relieved from " + his + " duties as ", BODY));
            l3.add(new Chunk(safe(r.designation()), BODY_B));
            l3.add(new Chunk(".", BODY));
            doc.add(l3);

            Paragraph l4 = new Paragraph("We wish " + him + " continued success in "
                    + his + " future endeavours.", BODY);
            l4.setSpacingBefore(10f);
            doc.add(l4);
        } else {
            Paragraph l2 = new Paragraph();
            l2.setAlignment(Element.ALIGN_JUSTIFIED);
            l2.setSpacingBefore(6f);
            l2.add(new Chunk("With reference to " + his + " resignation, " + he
                    + " has been relieved from " + his + " duties as ", BODY));
            l2.add(new Chunk(safe(r.designation()), BODY_B));
            l2.add(new Chunk(".", BODY));
            doc.add(l2);

            Paragraph l3 = new Paragraph("We wish " + him + " good luck for future assignments.", BODY);
            l3.setSpacingBefore(10f);
            doc.add(l3);
        }

        // Signatory — embed signature image if present, else blank space.
        Paragraph forCo = new Paragraph(
                "For TechNext Technologies and Services Private Limited.,", BODY);
        forCo.setSpacingBefore(40f);
        doc.add(forCo);

        addSignatureImageOrGap(doc, r.signatureFileId());

        doc.add(new Paragraph(safe(r.signatoryName()), BODY));
        doc.add(new Paragraph(safe(r.signatoryTitle()), BODY));
    }

    /**
     * Internship Experience Letter — a distinct single-page layout, same
     * shape as the Relieving/Experience letter (letterhead, "TO WHOMSOEVER
     * IT MAY CONCERN", certification paragraph, signatory block) but for an
     * intern who *completed* their internship rather than resigned. No
     * resignation/relieving language anywhere.
     *
     * r.internshipDetails() is free text HR types per intern describing
     * responsibilities/technologies/contributions — never hardcoded. If left
     * blank, that paragraph is simply skipped rather than rendering empty.
     */
    private void addInternshipOfferLetter(Document doc, LetterPdfRequest r, PdfWriter writer, LetterFonts f) throws DocumentException {
        addLetterhead(f, doc);
        addDateBlock(f, doc, r);
        addTitle(f, doc, "INTERNSHIP OFFER LETTER");

        doc.add(new Paragraph("To,", f.body()));
        doc.add(new Paragraph(safe(r.employeeName()), f.bodyB()));
        addAddressLines(f, doc, r);
        if (r.employeeAddress() != null && !r.employeeAddress().isBlank()) {
            doc.add(new Paragraph(" ", f.body()));
        }
        Paragraph subject = new Paragraph("Subject: Offer of Internship – " + safe(r.designation()), f.bodyB());
        subject.setSpacingBefore(2f);
        doc.add(subject);
        doc.add(new Paragraph("Dear " + firstName(r.employeeName()) + ",", f.body()));
        Paragraph intro = new Paragraph();
        intro.setAlignment(Element.ALIGN_JUSTIFIED);
        intro.add(new Chunk("We are pleased to offer you an internship opportunity with ", f.body()));
        intro.add(new Chunk("TechNext Technologies and Services Private Limited", f.bodyB()));
        intro.add(new Chunk(" for the position of ", f.body()));
        intro.add(new Chunk(safe(r.designation()), f.bodyB()));
        intro.add(new Chunk(". Based on your profile, skills, and interaction with our team, we believe that this internship will provide you with valuable practical exposure and an opportunity to develop your professional and technical skills.", f.body()));
        intro.setSpacingBefore(6f);
        doc.add(intro);
        doc.add(new Paragraph("Your internship will be governed by the following terms and conditions:", f.body()));

        clause(f, doc, 1, "Internship Position",
                "Designation: " + safe(r.designation()) + "\n" +
                "Department: " + safe(r.internshipDepartment()) + "\n" +
                "Reporting Manager: " + safe(r.internshipReportingManager()) + "\n" +
                "Location: " + safe(r.workLocation()));

        clause(f, doc, 2, "Internship Duration",
                "Your internship will commence on " + safe(r.dateOfJoining()) + " and will continue until " +
                safe(r.employmentEndDate()) + ".");

        String workingDays = safe(r.internshipWorkingDays());
        String startTime = safe(r.internshipStartTime());
        String endTime = safe(r.internshipEndTime());
        clause(f, doc, 3, "Working Hours",
                (workingDays.isBlank() ? "" : workingDays + " | ") +
                (startTime.isBlank() && endTime.isBlank() ? "" : startTime + " to " + endTime) +
                "\nYou are expected to maintain professional discipline, punctuality, and regular attendance throughout the internship.");

        // Keep the compensation selection mutually exclusive: the PDF contains
        // only the option chosen by HR, never both paid and unpaid content.
        Paragraph compTitle = new Paragraph("4. Stipend / Compensation", f.clauseT());
        compTitle.setSpacingBefore(8f);
        compTitle.setSpacingAfter(2f);
        doc.add(compTitle);
        String compensation = safe(r.internshipCompensationType()).trim().toUpperCase();
        if ("PAID".equals(compensation)) {
            Paragraph paid = new Paragraph("Paid Internship", f.bodyB());
            doc.add(paid);
            Paragraph paidBody = new Paragraph("You will be entitled to a monthly stipend of ₹" +
                    safe(r.internshipStipend()) + ", subject to applicable Company policies and satisfactory attendance and performance.", f.body());
            paidBody.setAlignment(Element.ALIGN_JUSTIFIED);
            doc.add(paidBody);
        } else {
            Paragraph unpaid = new Paragraph("Unpaid Internship", f.bodyB());
            doc.add(unpaid);
            Paragraph unpaidBody = new Paragraph("This internship is an unpaid internship, and no stipend or salary will be payable during the internship period.", f.body());
            unpaidBody.setAlignment(Element.ALIGN_JUSTIFIED);
            doc.add(unpaidBody);
        }

        clause(f, doc, 5, "Roles and Responsibilities",
                "During the internship, you will be expected to:\n" +
                "• Perform tasks and assignments allocated by your reporting manager.\n" +
                "• Participate actively in team meetings, training sessions, and project activities.\n" +
                "• Follow the Company's processes, policies, and instructions.\n" +
                "• Maintain professional communication and conduct.\n" +
                "• Complete assigned tasks within the agreed timelines.\n" +
                "• Maintain confidentiality of Company, client, employee, candidate, and project information.\n" +
                "• Continuously develop the skills relevant to your internship role.");

        clause(f, doc, 6, "Training and Learning",
                "During the internship, you may receive practical training, mentoring, project exposure, and guidance from members of the Company. The internship is intended to provide practical industry exposure and professional development. The Company may evaluate your performance periodically.");

        clause(f, doc, 7, "Performance Evaluation",
                "Your performance may be evaluated based on factors including:\n" +
                "• Attendance and punctuality\n• Quality of work\n• Technical/professional skills\n• Learning ability\n• Communication\n• Teamwork\n• Initiative and ownership\n• Meeting assigned targets and deadlines\n• Professional conduct\n\nBased on your overall performance, the Company may provide an Internship Completion Certificate upon successful completion of the internship.");

        clause(f, doc, 8, "Employment Opportunity",
                "Successful completion of the internship does not guarantee permanent employment with the Company. However, based on business requirements, performance, skills, and availability of suitable positions, the Company may consider you for a full-time employment opportunity. Any employment opportunity will be subject to a separate employment offer letter and applicable terms and conditions.");

        clause(f, doc, 9, "Confidentiality",
                "During your internship, you may have access to confidential information relating to the Company, its clients, employees, candidates, projects, technology, business processes, pricing, databases, software, and other proprietary information. You shall not disclose, copy, distribute, misuse, or share such information with any unauthorized person during or after the internship. You must immediately return or delete Company information, documents, credentials, and other materials upon completion or termination of your internship.");

        clause(f, doc, 10, "Intellectual Property",
                "Any work product, documents, designs, software, code, reports, databases, processes, content, or other materials created or developed by you during the internship in connection with Company work shall belong to TechNext Technologies and Services Private Limited, subject to applicable law and the specific terms of any separate agreement. You shall not use or distribute such materials for personal or commercial purposes without prior written authorization from the Company.");

        clause(f, doc, 11, "Company Policies",
                "You are required to comply with all applicable Company policies, procedures, information-security requirements, acceptable-use guidelines, and instructions communicated to you from time to time. Any violation of Company policies may result in disciplinary action, including termination of the internship.");

        clause(f, doc, 12, "Termination of Internship",
                "Either the Company or the Intern may terminate the internship by providing [7/15] days' notice, unless otherwise specified by the Company. The Company reserves the right to terminate the internship immediately in cases involving serious misconduct, breach of confidentiality, violation of Company policies, unauthorized absence, fraud, misuse of Company resources, or other serious violations.");

        clause(f, doc, 13, "Attendance and Leave",
                "You are expected to maintain regular attendance throughout the internship. Leave or absence must be approved in advance by your reporting manager, except in genuine emergencies. Repeated unauthorized absence or poor attendance may affect your internship evaluation and continuation.");

        clause(f, doc, 14, "Company Assets and Access",
                "Any laptop, ID card, software credentials, email account, documents, equipment, or other Company assets provided to you must be used only for authorized purposes. All Company assets and access credentials must be returned or surrendered upon completion or termination of the internship.");

        clause(f, doc, 15, "Declaration",
                "By accepting this offer, you confirm that:\n" +
                "• The information provided by you during the selection process is accurate.\n" +
                "• You will comply with Company policies and instructions.\n" +
                "• You will maintain confidentiality of Company and client information.\n" +
                "• You will perform your responsibilities professionally and diligently.\n" +
                "• You understand that the internship does not guarantee permanent employment.");

        Paragraph welcome = new Paragraph("We are pleased to welcome you to TechNext Technologies and Services Private Limited and look forward to your learning, contribution, and professional growth with us. We wish you a successful and rewarding internship experience.", f.body());
        welcome.setAlignment(Element.ALIGN_JUSTIFIED);
        welcome.setSpacingBefore(6f);
        doc.add(welcome);

        addSignatory(f, doc, r);
        addEmployeeAcceptance(f, doc, r, writer);
    }

    private void addInternshipBody(Document doc, LetterPdfRequest r) throws DocumentException {
        // Gender-aware pronouns (fall back to "their/them" if unknown) — same
        // convention as addRelievingBody, for consistency.
        String g = r.gender() == null ? "" : r.gender().trim().toUpperCase();
        boolean male = g.startsWith("M");
        boolean female = g.startsWith("F");
        String his = male ? "his" : female ? "her" : "their";
        String he = male ? "he" : female ? "she" : "they";
        String him = male ? "him" : female ? "her" : "them";

        Paragraph gap0 = new Paragraph(" ", BODY);
        gap0.setSpacingAfter(18f);
        doc.add(gap0);

        Paragraph title = new Paragraph("INTERNSHIP EXPERIENCE LETTER", TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10f);
        doc.add(title);

        Paragraph sub = new Paragraph("TO WHOMSOEVER IT MAY CONCERN", SUBTITLE);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(18f);
        doc.add(sub);

        doc.add(new Paragraph("Date: " + safe(r.letterDate()), BODY));
        Paragraph gap1 = new Paragraph(" ", BODY);
        gap1.setSpacingAfter(6f);
        doc.add(gap1);

        String start = safe(r.dateOfJoining());
        String end = safe(r.employmentEndDate());
        Paragraph line1 = new Paragraph();
        line1.setAlignment(Element.ALIGN_JUSTIFIED);
        line1.add(new Chunk("This is to certify that ", BODY));
        line1.add(new Chunk(safe(r.employeeName()), BODY_B));
        line1.add(new Chunk(
                " has successfully completed an internship with TechNext Technologies and Services Private Limited as ", BODY));
        line1.add(new Chunk(safe(r.designation()), BODY_B));
        line1.add(new Chunk(", from ", BODY));
        line1.add(new Chunk(start, BODY_B));
        line1.add(new Chunk(" to ", BODY));
        line1.add(new Chunk(end, BODY_B));
        line1.add(new Chunk(".", BODY));
        doc.add(line1);

        Paragraph l2 = new Paragraph();
        l2.setAlignment(Element.ALIGN_JUSTIFIED);
        l2.setSpacingBefore(6f);
        l2.add(new Chunk("During " + his + " internship, " + he
                + " was found to be sincere, hardworking, and professional in "
                + his + " conduct, and performed all assigned tasks and responsibilities to our satisfaction.", BODY));
        doc.add(l2);

        // HR-typed free text (responsibilities/technologies/contributions) —
        // only rendered when actually provided, never hardcoded.
        String details = safe(r.internshipDetails()).trim();
        if (!details.isBlank()) {
            Paragraph l3 = new Paragraph(details, BODY);
            l3.setAlignment(Element.ALIGN_JUSTIFIED);
            l3.setSpacingBefore(6f);
            doc.add(l3);
        }

        Paragraph l4 = new Paragraph("We appreciate " + his + " contributions during the internship and wish "
                + him + " all the best for " + his + " future career and professional endeavours.", BODY);
        l4.setSpacingBefore(10f);
        doc.add(l4);

        // Signatory — same block structure as addRelievingBody, for
        // consistency (embed signature image if present, else blank space).
        Paragraph forCo = new Paragraph(
                "For TechNext Technologies and Services Private Limited.,", BODY);
        forCo.setSpacingBefore(40f);
        doc.add(forCo);

        addSignatureImageOrGap(doc, r.signatureFileId());

        doc.add(new Paragraph(safe(r.signatoryName()), BODY));
        doc.add(new Paragraph(safe(r.signatoryTitle()), BODY));
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Contract-to-Hire (C2H) Offer Letter
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Recipient block for the C2H letter — same "To, / Name / Subject / Dear.."
     * shape as {@link #addRecipient}, but with the C2H-specific subject line
     * and intro sentence per the reference document. Kept as its own method
     * (rather than adding a branch inside addRecipient) so the existing Offer
     * Letter recipient text is never touched.
     */
    private void addC2HRecipient(Document doc, LetterPdfRequest r) throws DocumentException {
        doc.add(new Paragraph("To,", BODY));
        doc.add(new Paragraph(safe(r.employeeName()), BODY_B));
        addAddressLines(doc, r);
        Paragraph gap = new Paragraph(" ", BODY);
        gap.setSpacingAfter(4f);
        doc.add(gap);

        doc.add(new Paragraph("Subject: Offer of Employment \u2013 Contract-to-Hire (C2H)", BODY_B));
        doc.add(new Paragraph("Dear " + firstName(r.employeeName()) + ",", BODY));

        Paragraph intro = new Paragraph();
        intro.setAlignment(Element.ALIGN_JUSTIFIED);
        intro.add(new Chunk("With reference to the discussions held, we are pleased to offer you employment with ", BODY));
        intro.add(new Chunk("TechNext Technologies and Services Private Limited", BODY_B));
        intro.add(new Chunk(" (\"Company\") for the position of ", BODY));
        intro.add(new Chunk(safe(r.designation()), BODY_B));
        intro.add(new Chunk(" on a Contract-to-Hire (C2H) basis, subject to the following terms and conditions.", BODY));
        intro.setSpacingBefore(6f);
        doc.add(intro);
    }

    /**
     * C2H clause body — clauses 1 to 10 exactly as structured in the reference
     * Contract-to-Hire Offer Letter (Appointment Details, Nature of Employment,
     * Compensation, Working Hours and Duties, Background Verification,
     * Termination of Employment, Company Assets, Confidentiality and
     * Intellectual Property, Code of Conduct, Acceptance of Offer), followed by
     * the same Authorized Signatory + Employee Acceptance blocks used by the
     * standard Offer Letter (reused, not duplicated).
     */
    private void addC2HBody(Document doc, LetterPdfRequest r, PdfWriter writer) throws DocumentException {
        String duration = safe(r.contractDuration()).trim();
        String unit = unitLabel(r.contractDurationUnit());
        String durationPhrase = duration.isBlank() ? "the agreed initial period" : duration + " " + unit;

        clause(doc, 1, "Appointment Details",
                "You are being appointed as **" + safe(r.designation()) +
                "** on a Contract-to-Hire basis for an initial period of **" + durationPhrase +
                "**, commencing from **" + safe(r.dateOfJoining()) + "** and ending on **" + safe(r.employmentEndDate()) +
                "**, unless terminated earlier in accordance with this letter. Your work location shall be " +
                safe(r.workLocation()) + ", and you will report to a person designated by the Company or the client organization.");

        clause(doc, 2, "Nature of Employment",
                "This appointment is strictly contractual in nature and shall not be construed as permanent employment " +
                "with the Company or the client. Upon successful completion of the initial contract period, your engagement " +
                "may be extended by TechNext Technologies and Services Private Limited, or may be converted into a Full-Time " +
                "Employment (FTE) opportunity with the client organization based on your performance, attendance, conduct, " +
                "business requirements, and client approval. The decision regarding extension or conversion shall be solely " +
                "at the discretion of the Company and/or the client and shall be communicated in writing.");

        clause(doc, 3, "Compensation",
                "Your Annualized Cost to Company (CTC) shall be **INR " + safe(r.ctcAnnual()) +
                (r.ctcInWords() != null && !r.ctcInWords().isBlank() ? " (" + r.ctcInWords() + ")" : "") + "**" +
                ". The detailed salary structure and applicable statutory deductions are provided in **Annexure-A**. " +
                "Statutory deductions, including Provident Fund (PF), Professional Tax (PT), and Tax Deducted at Source " +
                "(TDS), if applicable, shall be made in accordance with prevailing laws and regulations.");

        clause(doc, 4, "Working Hours and Duties",
                "Your working hours shall be 8\u20139 hours per day, five (5) days a week, or as prescribed by the client " +
                "organization. You shall perform your duties diligently, adhere to all Company and client policies, and " +
                "always maintain professional conduct.");

        clause(doc, 5, "Background Verification",
                "Your appointment is subject to satisfactory background verification and validation of all documents " +
                "submitted by you. Any false declaration, suppression of facts, or discrepancies identified at any stage " +
                "may result in immediate termination without notice.");

        addC2HTerminationClause(doc);

        clause(doc, 7, "Company Assets",
                "You shall return all Company and/or client assets, including laptops, ID cards, access credentials, " +
                "documents, and any confidential materials upon cessation of employment. Failure to do so may result in " +
                "recovery proceedings.");

        clause(doc, 8, "Confidentiality and Intellectual Property",
                "You shall maintain strict confidentiality concerning all information pertaining to the Company, its " +
                "clients, employees, business operations, and proprietary information during and after your employment. " +
                "All work products, documents, databases, reports, and intellectual property developed during your " +
                "engagement shall remain the exclusive property of the Company and/or the client.");

        clause(doc, 9, "Code of Conduct",
                "You are required to comply with all Company and client policies, including but not limited to " +
                "attendance, information security, workplace ethics, and anti-harassment policies. Any violation may " +
                "result in disciplinary action, including termination.");

        clause(doc, 10, "Acceptance of Offer",
                "This offer is valid for five (5) working days from the date of issuance. Kindly sign and return a copy " +
                "of this letter as a token of your acceptance of the terms and conditions mentioned herein. We look " +
                "forward to having you as part of our team and wish you a successful association with TechNext " +
                "Technologies and Services Private Limited.");

        addSignatory(doc, r);
        addEmployeeAcceptance(doc, r, writer);
    }

    /**
     * Clause 6 ("Termination of Employment") has a bulleted list of
     * immediate-termination grounds in the reference document, which the
     * shared {@link #clause} helper (single body string) doesn't support —
     * so it's built directly here rather than forcing that helper to change.
     */
    private void addC2HTerminationClause(Document doc) throws DocumentException {
        Paragraph t = new Paragraph("6. Termination of Employment", CLAUSE_T);
        t.setSpacingBefore(8f);
        t.setSpacingAfter(2f);
        doc.add(t);

        Paragraph intro = new Paragraph("Either party may terminate this contract by providing fifteen (15) days' " +
                "written notice or salary in lieu thereof during the contract period.", BODY);
        intro.setAlignment(Element.ALIGN_JUSTIFIED);
        intro.setSpacingAfter(3f);
        doc.add(intro);

        Paragraph lead = new Paragraph("The Company reserves the right to terminate your employment immediately " +
                "without notice in cases of:", BODY);
        lead.setAlignment(Element.ALIGN_JUSTIFIED);
        lead.setSpacingAfter(2f);
        doc.add(lead);

        for (String reason : new String[]{
                "Misconduct", "Poor performance", "Breach of confidentiality",
                "Violation of Company or client policies", "Absenteeism or abandonment of employment",
                "Business or client requirements"}) {
            Paragraph b = new Paragraph("\u2022 " + reason, BODY);
            b.setIndentationLeft(18f);
            b.setSpacingAfter(1f);
            doc.add(b);
        }

        Paragraph closing = new Paragraph("The completion of the initial contract period does not guarantee " +
                "extension or absorption into permanent employment.", BODY);
        closing.setAlignment(Element.ALIGN_JUSTIFIED);
        closing.setSpacingBefore(4f);
        closing.setSpacingAfter(4f);
        doc.add(closing);
    }

    private void addLetterhead(Document doc) throws DocumentException {
        addLetterhead(STANDARD_FONTS, doc);
    }

    /** Font-parameterised twin of the method above (identical logic; only the fonts are injected). */
    private void addLetterhead(LetterFonts f, Document doc) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new int[]{1, 1});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Image logoImg = loadLogo();
        if (logoImg != null) {
            logoImg.scaleToFit(220f, 90f);   // bigger logo
            left.addElement(logoImg);
        } else {
            left.addElement(new Paragraph("TECH NEXT", H_LOGO));
            left.addElement(new Paragraph("EMPOWERING DATA ENGINEERS", H_SMALL));
        }
        t.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);   // centre contact beside logo
        for (String line : new String[]{"080 41515964", "Info@technnext.com", "www.technnext.com"}) {
            Paragraph p = new Paragraph(line, f.contact());
            p.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(p);
        }
        t.addCell(right);
        doc.add(t);

        // Header blue line REMOVED (per request) — just add spacing before the body.
        Paragraph headSpace = new Paragraph(" ", f.body());
        headSpace.setSpacingAfter(8f);
        doc.add(headSpace);
    }

    /**
     * Loads the company logo from src/main/resources/letter-logo.png (or fallbacks).
     * Returns null (letterhead falls back to text) if not present.
     */
    private Image loadLogo() {
        for (String name : new String[]{"letter-logo.png", "letter-logo.jpg", "letter-logo.jpeg", "logo.png", "logo.jpg"}) {
            try {
                ClassPathResource res = new ClassPathResource(name);
                if (res.exists()) {
                    try (InputStream in = res.getInputStream()) {
                        return Image.getInstance(in.readAllBytes());
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Loads the authorised signature from src/main/resources/signature.png (or
     * fallbacks). Returns null if not present.
     */
    private Image loadSignature() {
        for (String name : new String[]{"signature.png", "signature.jpg", "signature.jpeg", "sign.png", "sign.jpg"}) {
            try {
                ClassPathResource res = new ClassPathResource(name);
                if (res.exists()) {
                    try (InputStream in = res.getInputStream()) {
                        return Image.getInstance(in.readAllBytes());
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * #14: Loads an uploaded HR Director signature by stored-file id. Accepts a
     * bare UUID or a "/api/files/{id}" style url. Returns null on any problem so
     * the caller can fall back to the bundled signature or blank space.
     */
    private Image loadSignatureFromFile(String signatureFileId) {
        if (signatureFileId == null || signatureFileId.isBlank()) return null;
        try {
            String idPart = signatureFileId.trim();
            if (idPart.contains("/")) {
                // e.g. "/api/files/{id}" -> take the last non-empty segment
                String[] parts = idPart.split("/");
                for (int i = parts.length - 1; i >= 0; i--) {
                    if (!parts[i].isBlank()) { idPart = parts[i]; break; }
                }
            }
            StoredFile sf = fileStorageService.load(UUID.fromString(idPart));
            if (sf != null && sf.getData() != null) {
                return Image.getInstance(sf.getData());
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Embeds the signature image above the name, otherwise leaves vertical blank
     * space for a physical signature. #14: prefers an uploaded signature
     * (signatureFileId) and falls back to the bundled signature.png.
     *
     * Added as a standalone block Image (not an inline Chunk) so it always
     * starts on its own line, left-aligned with the surrounding text (the
     * "For TechNext..." line above and Name/Designation/Date below), with
     * clear space above and below. An inline Chunk(image, 0, 0) anchors the
     * image to the current text cursor and can render it overlapping the end
     * of the preceding line — Image.LEFT as a standalone block avoids that.
     */
    private void addSignatureImageOrGap(Document doc, String signatureFileId) throws DocumentException {
        addSignatureImageOrGap(STANDARD_FONTS, doc, signatureFileId);
    }

    /** Font-parameterised twin of the method above (identical logic; only the fonts are injected). */
    private void addSignatureImageOrGap(LetterFonts f, Document doc, String signatureFileId) throws DocumentException {
        Image sig = loadSignatureFromFile(signatureFileId);
        if (sig == null) sig = loadSignature();
        if (sig != null) {
            sig.scaleToFit(150f, 60f);
            sig.setAlignment(Image.LEFT);
            sig.setSpacingBefore(6f);
            sig.setSpacingAfter(5f);
            doc.add(sig);
        } else {
            Paragraph sigSpace = new Paragraph(" ", f.body());
            sigSpace.setSpacingAfter(18f);
            doc.add(sigSpace);
        }
    }

    /**
     * Draws, on EVERY page: a faint centered logo watermark (behind content) and
     * the address + CIN footer with a brand-cyan rule.
     */
    static class PageDecorator extends PdfPageEventHelper {
        private Image mark;
        private boolean tried;
        private static final Color CYAN = new Color(0x00, 0xD4, 0xF0);
        private static final Color GREY = new Color(0x33, 0x33, 0x33);
        private final Font foot;

        PageDecorator() {
            this(new Font(TIMES_REGULAR, 12, Font.NORMAL, GREY));
        }

        PageDecorator(Font foot) {
            this.foot = foot;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            drawWatermark(writer, document);
            drawFooter(writer, document);
        }

        private void drawWatermark(PdfWriter writer, Document document) {
            try {
                if (!tried) {
                    tried = true;
                    for (String name : new String[]{"letter-logo.png", "letter-logo.jpg", "letter-logo.jpeg", "logo.png", "logo.jpg"}) {
                        ClassPathResource res = new ClassPathResource(name);
                        if (res.exists()) {
                            try (InputStream in = res.getInputStream()) {
                                mark = Image.getInstance(in.readAllBytes());
                            }
                            break;
                        }
                    }
                }
                if (mark == null) return;
                PdfContentByte under = writer.getDirectContentUnder();
                under.saveState();
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(0.06f);
                under.setGState(gs);
                float w = 300f;
                float h = w * mark.getHeight() / mark.getWidth();
                float x = (document.getPageSize().getWidth() - w) / 2f;
                float y = (document.getPageSize().getHeight() - h) / 2f;
                Image copy = Image.getInstance(mark);
                copy.setAbsolutePosition(x, y);
                copy.scaleToFit(w, h);
                under.addImage(copy);
                under.restoreState();
            } catch (Exception ignored) {}
        }

        private void drawFooter(PdfWriter writer, Document document) {
            try {
                PdfContentByte cb = writer.getDirectContent();
                float cx = document.getPageSize().getWidth() / 2f;
                float ruleY = document.bottom() - 8f;
                // brand-cyan rule
                cb.saveState();
                cb.setColorStroke(CYAN);
                cb.setLineWidth(1.2f);
                cb.moveTo(document.left(), ruleY);
                cb.lineTo(document.right(), ruleY);
                cb.stroke();
                cb.restoreState();
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                        new Phrase("Address: TechNext Technologies and services Pvt Ltd, Novel MSR Tech Park,", foot),
                        cx, ruleY - 16f, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                        new Phrase("Marathahalli, Bangalore - 560037 | CIN: U62013KA2026PTC215474", foot),
                        cx, ruleY - 32f, 0);
            } catch (Exception ignored) {}
        }
    }

    private void addDateBlock(Document doc, LetterPdfRequest r) throws DocumentException {
        addDateBlock(STANDARD_FONTS, doc, r);
    }

    /** Font-parameterised twin of the method above (identical logic; only the fonts are injected). */
    private void addDateBlock(LetterFonts f, Document doc, LetterPdfRequest r) throws DocumentException {
        // Labels ("Date:"/"Place:") bold, values normal — matches the
        // reference letter's convention; previously the whole line was
        // plain BODY (no bold at all).
        Paragraph d = new Paragraph();
        d.add(new Chunk("Date: ", f.bodyB()));
        d.add(new Chunk(safe(r.letterDate()), f.body()));
        d.setAlignment(Element.ALIGN_RIGHT);
        doc.add(d);
        Paragraph p = new Paragraph();
        p.add(new Chunk("Place: ", f.bodyB()));
        p.add(new Chunk(safe(r.place()), f.body()));
        p.setAlignment(Element.ALIGN_RIGHT);
        doc.add(p);
    }

    private void addTitle(Document doc, String title) throws DocumentException {
        addTitle(STANDARD_FONTS, doc, title);
    }

    /** Font-parameterised twin of the method above (identical logic; only the fonts are injected). */
    private void addTitle(LetterFonts f, Document doc, String title) throws DocumentException {
        Paragraph t = new Paragraph(title, f.title());
        t.setAlignment(Element.ALIGN_CENTER);
        t.setSpacingBefore(12f);
        t.setSpacingAfter(12f);
        doc.add(t);
    }

    private void addRecipient(Document doc, LetterPdfRequest r, boolean appointment) throws DocumentException {
        // Offer and Appointment recipient blocks both use "To," (with comma)
        // per the reference letter format — this previously only applied to
        // Appointment, leaving the Offer Letter's "To" (no comma) out of
        // sync. C2H's own addC2HRecipient() already used "To," and is
        // untouched here.
        doc.add(new Paragraph("To,", BODY));
        doc.add(new Paragraph(safe(r.employeeName()), BODY_B));
        addAddressLines(doc, r);
        doc.add(new Paragraph(" ", BODY));
        if (appointment) {
            doc.add(new Paragraph("Dear " + safe(r.employeeName()) + ",", BODY));
            Paragraph intro = new Paragraph();
            intro.setAlignment(Element.ALIGN_JUSTIFIED);
            intro.add(new Chunk("With reference to your application and subsequent discussions, we are pleased to appoint you as ", BODY));
            intro.add(new Chunk(safe(r.designation()), BODY_B));
            intro.add(new Chunk(" with ", BODY));
            intro.add(new Chunk("TechNext Technologies and Services Private Limited", BODY_B));
            intro.add(new Chunk(" (\"Company\") with effect from ", BODY));
            intro.add(new Chunk(safe(r.dateOfJoining()), BODY_B));
            intro.add(new Chunk(", subject to the terms and conditions set forth in this letter.", BODY));
            intro.setSpacingBefore(6f);
            doc.add(intro);
        } else {
            doc.add(new Paragraph("Subject: Offer of Employment", BODY_B));
            doc.add(new Paragraph("Dear " + firstName(r.employeeName()) + ",", BODY));
            Paragraph intro = new Paragraph();
            intro.setAlignment(Element.ALIGN_JUSTIFIED);
            intro.add(new Chunk("With reference to the discussions held, we are pleased to offer you employment with ", BODY));
            intro.add(new Chunk("TechNext Technologies and Services Private Limited", BODY_B));
            intro.add(new Chunk(" (\"Company\") for the position of ", BODY));
            intro.add(new Chunk(safe(r.designation()), BODY_B));
            intro.add(new Chunk(", subject to the following terms and conditions.", BODY));
            intro.setSpacingBefore(6f);
            doc.add(intro);
        }
    }

    /**
     * Prints the candidate's current address as one line per address
     * component (name → address → Subject/blank-gap → Dear...), exactly
     * matching the reference letter's layout. Non-bold, regular body text.
     * Prints nothing at all when no address is available (typed-but-
     * unmatched candidates, or an employee record with no address on file)
     * — never invents or hardcodes a placeholder.
     */
    private void addAddressLines(Document doc, LetterPdfRequest r) throws DocumentException {
        addAddressLines(STANDARD_FONTS, doc, r);
    }

    /** Font-parameterised twin of the method above (identical logic; only the fonts are injected). */
    private void addAddressLines(LetterFonts f, Document doc, LetterPdfRequest r) throws DocumentException {
        String address = r.employeeAddress();
        if (address == null || address.isBlank()) return;
        for (String line : address.split("\n")) {
            if (line.isBlank()) continue;
            doc.add(new Paragraph(line.trim(), f.body()));
        }
    }

    /**
     * Renders a numbered clause. `body` supports simple **bold** markers
     * (e.g. "appointed as **Software Engineer** for **6 months**") which are
     * split into bold/normal Chunks matching the reference letter's inline
     * emphasis (designation, dates, CTC amount, etc.) — a plain string with
     * no ** markers renders exactly as before, so every existing call site
     * is unaffected until it opts in by adding markers.
     */
    private void clause(Document doc, int n, String title, String body) throws DocumentException {
        clause(STANDARD_FONTS, doc, n, title, body);
    }

    /** Font-parameterised twin of the method above (identical logic; only the fonts are injected). */
    private void clause(LetterFonts f, Document doc, int n, String title, String body) throws DocumentException {
        Paragraph t = new Paragraph(n + ". " + title, f.clauseT());
        t.setSpacingBefore(8f);
        t.setSpacingAfter(2f);
        doc.add(t);
        Paragraph b = new Paragraph();
        b.setAlignment(Element.ALIGN_JUSTIFIED);
        b.setSpacingAfter(4f);
        String[] parts = body.split("\\*\\*", -1);
        // Odd indices (1, 3, 5...) are the text that was between ** markers.
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            b.add(new Chunk(parts[i], i % 2 == 1 ? f.bodyB() : f.body()));
        }
        doc.add(b);
    }

    private void addOfferBody(Document doc, LetterPdfRequest r, PdfWriter writer) throws DocumentException {
        clause(doc, 1, "Appointment Details", "You are offered the position of **" + safe(r.designation()) +
                "** on a " + employmentBasisPhrase(r) + ". Your work location shall be " + safe(r.workLocation()) +
                ". Your date of joining will be **" + safe(r.dateOfJoining()) + "**, and you will report to a person assigned by the Company.");
        clause(doc, 2, "Probation", "You will be on probation for a period of six (6) months from your date of joining. Your performance, conduct, and suitability will be reviewed during this period. Confirmation of employment is not automatic and will be communicated in writing by the Company.");
        clause(doc, 3, "Compensation", "Your Annual Cost to Company (CTC) will be **INR " + safe(r.ctcAnnual()) + "**" +
                ". The detailed salary structure and breakup are provided in **Annexure-A**. Statutory deductions such as PF, Professional Tax, and TDS shall apply as per applicable laws." +
                (hasVariablePay(r)
                        ? " Your compensation includes a Variable Pay component, as detailed in Annexure-A."
                        : " There is no variable pay or joining bonus applicable for this role."));
        clause(doc, 4, "Working Hours and Duties", "Your working hours will be 8-9 hours per day, five days a week. You shall perform all duties assigned to you diligently and comply with all Company policies, rules, and regulations.");
        clause(doc, 5, "Background Verification", "Your employment is subject to successful background verification. Any discrepancy, misrepresentation, or false information identified at any stage may result in immediate termination without notice.");
        clause(doc, 6, "Termination of Employment", "The Company reserves the absolute right to terminate your employment at any time, with or without notice or compensation, during probation or after confirmation, based on performance, misconduct, policy violations, confidentiality breaches, or business requirements. If you resign, you are required to serve 30 days' notice during probation and 60 days' notice after confirmation, or salary in lieu thereof.");
        clause(doc, 7, "Company Assets", "You shall return all Company assets, including laptop, ID card, access credentials, and documents, upon resignation or termination. Failure to return assets may result in deductions or legal recovery.");
        clause(doc, 8, "Confidentiality and Intellectual Property", "You shall maintain strict confidentiality of Company and client information during and after employment. All work, data, and intellectual property created during your employment shall be the sole property of the Company.");
        clause(doc, 9, "Acceptance of Offer", "This offer is valid for five (5) working days from the date of issue. Please sign and return a copy of this letter as a token of acceptance.");
        addSignatory(doc, r);
        addEmployeeAcceptance(doc, r, writer);
    }

    private void addAppointmentBody(Document doc, LetterPdfRequest r) throws DocumentException {
        clause(doc, 1, "Appointment and Role", "You are appointed as a " + employmentTypeNoun(r) + " of the Company in the role of **" + safe(r.designation()) +
                "** and shall be based at " + safe(r.workLocation()) + ". You will report to such person as may be designated by the Company. Your roles and responsibilities shall be assigned and modified from time to time based on business requirements. You shall devote your full working time, attention, and abilities to the business of the Company and shall not engage in any other employment, assignment, or business activity without prior written consent.");
        clause(doc, 2, "Probation", "You shall be on probation for a period of six (6) months from the date of joining. During this period, your performance, conduct, and suitability for the role will be evaluated. The Company reserves the right to extend, curtail, or terminate the probation period at its sole discretion. Confirmation of your employment shall be communicated in writing and shall not be deemed automatic.");
        clause(doc, 3, "Compensation", "Your annual Cost to Company (CTC) shall be **\u20B9" + safe(r.ctcAnnual()) +
                (r.ctcInWords() != null && !r.ctcInWords().isBlank() ? " (" + r.ctcInWords() + ")" : "") + "**" +
                ". Your salary shall be paid on a monthly basis and shall be subject to statutory deductions including Provident Fund, Professional Tax, and Income Tax, as applicable under prevailing laws. The detailed salary structure is provided in **Annexure-A** attached hereto and forms an integral part of this appointment letter. The Company reserves the right to revise or restructure your compensation based on performance, business requirements, or statutory changes.");
        clause(doc, 4, "Duties and Responsibilities", "You shall perform your duties diligently, efficiently, and in the best interests of the Company. You are required to comply with all lawful instructions issued by the Company and maintain the highest standards of integrity, discipline, and professionalism. You shall not accept any commission, benefit, or gratification from any third party in connection with Company business.");
        clause(doc, 5, "Company Policies", "Your employment shall be governed by the policies, rules, and regulations of the Company, including but not limited to the code of conduct, leave policy, IT and data security policies, and disciplinary procedures. These policies may be amended from time to time, and you shall be required to comply with such amendments.");
        clause(doc, 6, "Working Hours and Leave", "Your working hours shall ordinarily be eight to nine (8-9) hours per day for five working days a week. You may be required to work beyond standard working hours based on business requirements without additional compensation. You shall be entitled to leave as per the Company's leave policy in force from time to time. All leave requests must be approved in advance except in cases of genuine emergencies.");
        clause(doc, 7, "Confidentiality and Data Protection", "During the course of your employment, you may have access to confidential and proprietary information relating to the Company and its clients. You shall maintain strict confidentiality of such information and shall not disclose or use it for any purpose other than the performance of your duties. This obligation shall survive the termination of your employment. Any breach of confidentiality or data protection shall result in disciplinary action, including termination and legal proceedings.");
        clause(doc, 8, "Intellectual Property", "All intellectual property, including but not limited to documents, reports, databases, systems, processes, and any work created or developed by you during the course of your employment shall be the sole and exclusive property of the Company. You hereby assign all rights, title, and interest in such intellectual property to the Company.");
        clause(doc, 9, "Background Verification", "Your appointment is subject to satisfactory background verification. In the event that any information provided by you is found to be false, misleading, or incomplete, the Company reserves the right to terminate your employment immediately without notice or compensation.");
        clause(doc, 10, "Non-Compete and Non-Solicitation", "During your employment and for a reasonable period thereafter, you shall not engage in any activity that competes with the business of the Company, solicit Company clients, or induce employees to leave the Company.");
        clause(doc, 11, "Termination", "During the probation period, either party may terminate employment by providing thirty (30) days' written notice. Upon confirmation, the notice period shall be sixty (60) days or salary in lieu thereof. Notwithstanding the above, the Company reserves the right to terminate your employment without notice or compensation in cases of misconduct, breach of Company policies, violation of confidentiality, fraud, misrepresentation, or any act detrimental to the interests of the Company.");
        clause(doc, 12, "Return of Company Property", "Upon termination of your employment, you shall immediately return all Company property in your possession, including but not limited to laptop, ID card, documents, access credentials, and any other materials belonging to the Company. You shall not retain any copies of Company data.");
        clause(doc, 13, "Governing Law and Jurisdiction", "This agreement shall be governed by the laws of India, and the courts of Bangalore shall have exclusive jurisdiction over any disputes arising out of or in connection with this employment.");
        clause(doc, 14, "Superseding Clause", "This appointment letter constitutes the entire agreement between you and the Company and supersedes all prior discussions, communications, or representations, whether oral or written.");
        addSignatory(doc, r);
    }

    private void addSignatory(Document doc, LetterPdfRequest r) throws DocumentException {
        addSignatory(STANDARD_FONTS, doc, r);
    }

    /** Font-parameterised twin of the method above (identical logic; only the fonts are injected). */
    private void addSignatory(LetterFonts f, Document doc, LetterPdfRequest r) throws DocumentException {
        Paragraph p = new Paragraph("For TechNext Technologies and Services Private Limited", f.bodyB());
        p.setSpacingBefore(8f);
        doc.add(p);
        // signature image if present, else blank space for manual signing
        addSignatureImageOrGap(f, doc, r.signatureFileId());
        // Labels bold, values normal — matches reference; previously the
        // whole "Name: X" / "Designation: X" / "Date: X" line was plain BODY.
        Paragraph nameLine = new Paragraph();
        nameLine.add(new Chunk("Name: ", f.bodyB()));
        nameLine.add(new Chunk(safe(r.signatoryName()), f.body()));
        doc.add(nameLine);
        Paragraph desigLine = new Paragraph();
        desigLine.add(new Chunk("Designation: ", f.bodyB()));
        desigLine.add(new Chunk(safe(r.signatoryTitle()), f.body()));
        doc.add(desigLine);
        Paragraph dateLine = new Paragraph();
        dateLine.add(new Chunk("Date: ", f.bodyB()));
        dateLine.add(new Chunk(safe(r.letterDate()), f.body()));
        doc.add(dateLine);
    }

    /**
     * Employee Acceptance — added directly after the HR Director/signatory
     * block in the Offer Letter. The employee name is the same
     * r.employeeName() value already used throughout the letter (Recipient,
     * Annexure-A, etc.) — never a separate/hardcoded name.
     *
     * Explicitly checks the ACTUAL remaining space on the page (via the
     * PdfWriter) before deciding whether this needs a fresh page, rather
     * than relying solely on PdfPTable's own setKeepTogether(true)
     * space-estimation — real generated letters showed that heuristic
     * pushing this section onto its own near-empty page even when a large
     * gap was clearly visible above it. setKeepTogether(true) is still kept
     * as a safety net for the (now rare) case this estimate is close, so
     * the block is never split awkwardly mid-way even if it does need to
     * move to a new page.
     */
    private void addEmployeeAcceptance(Document doc, LetterPdfRequest r, PdfWriter writer) throws DocumentException {
        addEmployeeAcceptance(STANDARD_FONTS, doc, r, writer);
    }

    /** Font-parameterised twin of the method above (identical logic; only the fonts are injected). */
    private void addEmployeeAcceptance(LetterFonts f, Document doc, LetterPdfRequest r, PdfWriter writer) throws DocumentException {
        // Rough but deliberately generous estimate of this block's printed
        // height: heading + intro line (up to 2 wrapped lines) + 3 signature
        // lines, each with their own leading/spacing — see the literal
        // spacingAfter/font-size values used below. Overestimating here only
        // costs an occasional harmless extra page break; underestimating is
        // what would let content spill past the footer.
        float estimatedHeight = 16f + 5f          // heading + its spacing
                + (2 * 12f * 1.2f) + 10f          // intro line, up to 2 lines wrapped, + spacing
                + (12f * 1.2f + 7f) * 2           // Signature / Date lines + spacing
                + (12f * 1.2f);                    // Place line (no trailing spacing)
        float availableHeight = writer.getVerticalPosition(true) - doc.bottomMargin();
        if (availableHeight < estimatedHeight) {
            doc.newPage();
        }

        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setKeepTogether(true);
        box.setSpacingBefore(12f);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);

        Paragraph heading = new Paragraph("Employee Acceptance", f.clauseT());
        heading.setSpacingAfter(5f);
        cell.addElement(heading);

        Paragraph body = new Paragraph();
        body.setAlignment(Element.ALIGN_JUSTIFIED);
        body.add(new Chunk("I, ", f.body()));
        body.add(new Chunk(safe(r.employeeName()), f.bodyB()));
        body.add(new Chunk(
                ", have read, understood, and accepted the terms and conditions mentioned in this Offer Letter.",
                f.body()));
        body.setSpacingAfter(10f);
        cell.addElement(body);

        // Labels bold, blank/rule normal — matches reference; previously
        // the whole line ("Signature: ___", "Date: ___", "Place: ___") was
        // plain BODY with no bold at all.
        Paragraph sig = new Paragraph();
        sig.add(new Chunk("Signature: ", f.bodyB()));
        sig.add(new Chunk("______________________________", f.body()));
        sig.setSpacingAfter(7f);
        cell.addElement(sig);

        Paragraph date = new Paragraph();
        date.add(new Chunk("Date: ", f.bodyB()));
        date.add(new Chunk("__________________________________", f.body()));
        date.setSpacingAfter(7f);
        cell.addElement(date);

        Paragraph place = new Paragraph();
        place.add(new Chunk("Place: ", f.bodyB()));
        place.add(new Chunk("__________________________________", f.body()));
        cell.addElement(place);

        box.addCell(cell);
        doc.add(box);
    }

    private void addSalaryAnnexure(Document doc, LetterPdfRequest r, boolean appointment) throws DocumentException {
        doc.newPage();
        Paragraph h = new Paragraph(appointment ? "ANNEXURE - A (COMPENSATION STRUCTURE)" : "Annexure-A: Salary Structure", ANNEXURE_TITLE);
        h.setAlignment(Element.ALIGN_CENTER);
        h.setSpacingBefore(6f);
        h.setSpacingAfter(10f);
        doc.add(h);
        // Name / Designation / CTC — centered as one info block directly under
        // the heading, using real paragraph alignment (not manual whitespace or
        // fixed X coordinates, so it centers correctly against the actual page
        // width regardless of page size).
        Paragraph nameP = new Paragraph("Name: " + safe(r.employeeName()), BODY_B);
        nameP.setAlignment(Element.ALIGN_CENTER);
        doc.add(nameP);
        Paragraph desigP = new Paragraph("Designation: " + safe(r.designation()), BODY_B);
        desigP.setAlignment(Element.ALIGN_CENTER);
        doc.add(desigP);
        Paragraph ctcP = new Paragraph("Total CTC (Per Annum): " + safe(r.ctcAnnual()), BODY_B);
        ctcP.setAlignment(Element.ALIGN_CENTER);
        ctcP.setSpacingAfter(10f);
        doc.add(ctcP);

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.2f, 6f, 2.4f, 2.4f});

        headerCell(t, "Sr. No"); headerCell(t, "Salary Breakup"); headerCell(t, "Monthly (\u20B9)"); headerCell(t, "Annual (\u20B9)");

        sectionRow(t, "A", "Earnings");
        row(t, "i", "Basic Salary", r.basicM(), r.basicA());
        row(t, "ii", "HRA (40% of Basic)", r.hraM(), r.hraA());
        row(t, "iii", "Leave Travel Allowance", r.ltaM(), r.ltaA());
        row(t, "iv", "Special Allowance", r.specialM(), r.specialA());
        totalRow(t, "Gross Salary (E)", r.grossM(), r.grossA(), LIGHTBLUE);
        sectionRow(t, "B", "Employee Deductions");
        row(t, "i", "Employee PF", r.pfEmployeeM(), r.pfEmployeeA());
        row(t, "ii", "Professional Tax (KA)", r.ptM(), r.ptA());
        totalRow(t, "Total Deductions (D)", r.deductionsM(), r.deductionsA(), LIGHTBLUE);
        totalRowBlue(t, "Net Take Home (Before TDS)", r.netM(), r.netA());
        sectionRow(t, "D", "Employer Costs Included in CTC");
        row(t, "i", "Employer PF", r.pfEmployerM(), r.pfEmployerA());
        row(t, "ii", "Gratuity (4.81% of Basic)", r.gratuityM(), r.gratuityA());
        row(t, "iii", "Group Health/Accident Insurance", r.insuranceM(), r.insuranceA());
        if (hasVariablePay(r)) {
            row(t, "iv", "Variable Pay", r.variablePayM(), r.variablePayA());
        }
        totalRow(t, "Total Employer Cost", r.employerCostM(), r.employerCostA(), LIGHTBLUE);
        totalRowBlue(t, "Total Cost to Company (CTC)", r.ctcMonthlyTotal(), r.ctcAnnualTotal());

        doc.add(t);

        Paragraph notes = new Paragraph("Notes:", CELL_B);
        notes.setSpacingBefore(10f);
        doc.add(notes);
        Paragraph n1 = new Paragraph();
        n1.add(new Chunk("1. Compensation: ", CELL_B));
        n1.add(new Chunk("The above salary structure represents Cost to Company (CTC). Salary will be paid monthly and is subject to statutory deductions (PF, Professional Tax, Income Tax) as applicable. The Company reserves the right to revise the structure as per business or statutory requirements.", CELL));
        n1.setAlignment(Element.ALIGN_JUSTIFIED);
        doc.add(n1);
        Paragraph n2 = new Paragraph();
        n2.add(new Chunk("2. Confidentiality: ", CELL_B));
        n2.add(new Chunk("The employee must maintain strict confidentiality of all company and client information during and after employment. Any breach may lead to disciplinary action.", CELL));
        n2.setAlignment(Element.ALIGN_JUSTIFIED);
        n2.setSpacingBefore(3f);
        doc.add(n2);
    }

    // ---- table cell helpers ----
    private void headerCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, CELL_W));
        c.setBackgroundColor(BLUE);
        c.setPadding(4f);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
    }
    private void sectionRow(PdfPTable t, String sr, String label) {
        cell(t, sr, LIGHTBLUE, CELL_B, Element.ALIGN_LEFT);
        cell(t, label, LIGHTBLUE, CELL_B, Element.ALIGN_LEFT);
        cell(t, "", LIGHTBLUE, CELL_B, Element.ALIGN_RIGHT);
        cell(t, "", LIGHTBLUE, CELL_B, Element.ALIGN_RIGHT);
    }
    private void row(PdfPTable t, String sr, String label, String m, String a) {
        cell(t, sr, null, CELL, Element.ALIGN_LEFT);
        cell(t, label, null, CELL, Element.ALIGN_LEFT);
        cell(t, safe(m), null, CELL, Element.ALIGN_RIGHT);
        cell(t, safe(a), null, CELL, Element.ALIGN_RIGHT);
    }
    private void totalRow(PdfPTable t, String label, String m, String a, Color bg) {
        cell(t, "", bg, CELL_B, Element.ALIGN_LEFT);
        cell(t, label, bg, CELL_B, Element.ALIGN_LEFT);
        cell(t, safe(m), bg, CELL_B, Element.ALIGN_RIGHT);
        cell(t, safe(a), bg, CELL_B, Element.ALIGN_RIGHT);
    }
    private void totalRowBlue(PdfPTable t, String label, String m, String a) {
        cell(t, "", BLUE, CELL_W, Element.ALIGN_LEFT);
        cell(t, label, BLUE, CELL_W, Element.ALIGN_LEFT);
        cell(t, safe(m), BLUE, CELL_W, Element.ALIGN_RIGHT);
        cell(t, safe(a), BLUE, CELL_W, Element.ALIGN_RIGHT);
    }
    private void cell(PdfPTable t, String text, Color bg, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        if (bg != null) c.setBackgroundColor(bg);
        c.setPadding(3f);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    /**
     * NEW — turns employmentType (+ contractDuration/Unit for CONTRACT) into the
     * phrase used in clause 1 of the Offer letter, e.g. "full-time basis",
     * "part-time basis", or "contract basis for a period of 6 months from the
     * date of joining".
     */
    private String employmentBasisPhrase(LetterPdfRequest r) {
        String type = r.employmentType() == null ? "FULL_TIME" : r.employmentType().trim().toUpperCase();
        switch (type) {
            case "PART_TIME":
                return "part-time basis";
            case "CONTRACT":
                String dur = safe(r.contractDuration()).trim();
                String unit = unitLabel(r.contractDurationUnit());
                if (!dur.isBlank()) {
                    return "contract basis for a period of " + dur + " " + unit + " from the date of joining";
                }
                return "contract basis";
            default:
                return "full-time basis";
        }
    }

    /**
     * Same as {@link #employmentBasisPhrase} but as a noun phrase for the
     * Appointment letter, e.g. "full-time employee", "part-time employee", or
     * "contract employee, engaged for a period of 6 months from the date of
     * joining,".
     */
    private String employmentTypeNoun(LetterPdfRequest r) {
        String type = r.employmentType() == null ? "FULL_TIME" : r.employmentType().trim().toUpperCase();
        switch (type) {
            case "PART_TIME":
                return "part-time employee";
            case "CONTRACT":
                String dur = safe(r.contractDuration()).trim();
                String unit = unitLabel(r.contractDurationUnit());
                if (!dur.isBlank()) {
                    return "contract employee, engaged for a period of " + dur + " " + unit + " from the date of joining,";
                }
                return "contract employee";
            default:
                return "full-time employee";
        }
    }

    private String unitLabel(String unit) {
        if (unit == null) return "months";
        String u = unit.trim().toUpperCase();
        return u.startsWith("DAY") ? "days" : "months";
    }

    private String safe(String s) { return s == null ? "" : s; }

    /**
     * True only when a meaningful (> 0) Variable Pay amount was actually
     * sent — i.e. the frontend's toggle was "Yes" and an amount was typed.
     * When the toggle is "No", the frontend sends blank/omits the field, so
     * this returns false and the Annexure-A row is skipped entirely rather
     * than printing a spurious "Variable Pay: 0" line.
     */
    private boolean hasVariablePay(LetterPdfRequest r) {
        String a = r.variablePayA();
        if (a == null || a.isBlank()) return false;
        String digits = a.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return false;
        try {
            return Long.parseLong(digits) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    private String cap(String s) { return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private String firstName(String full) {
        if (full == null || full.isBlank()) return "";
        String[] parts = full.trim().split("\\s+");
        return parts.length > 1 ? parts[parts.length - 1] : parts[0];
    }
}