package com.technnext.hrms.letter.service;
import com.lowagie.text.*;
import com.lowagie.text.Image;
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
import java.io.InputStream;
import java.util.UUID;
/**
 * Generates formatted Offer / Appointment / Relieving letter PDFs (letterhead,
 * clauses, salary table) using OpenPDF. HR-entered values are merged in.
 *
 * Fonts: whole document uses Times New Roman (Font.TIMES_ROMAN).
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
    // All fonts Times New Roman.
    private static final Font H_LOGO = new Font(Font.TIMES_ROMAN, 18, Font.BOLD, BLUE);
    private static final Font H_SMALL = new Font(Font.TIMES_ROMAN, 7, Font.NORMAL, GREY);
    private static final Font CONTACT = new Font(Font.TIMES_ROMAN, 12, Font.NORMAL, Color.BLACK);
    // Main letter heading ("OFFER LETTER" / "APPOINTMENT LETTER" / "EMPLOYMENT
    // SERVICE LETTER" / "EXPERIENCE CUM RELIEVING LETTER").
    private static final Font TITLE = new Font(Font.TIMES_ROMAN, 16, Font.BOLD, Color.BLACK);
    // Annexure-A heading specifically — deliberately a different size (14pt) per
    // spec, so it needed its own constant rather than sharing TITLE.
    private static final Font ANNEXURE_TITLE = new Font(Font.TIMES_ROMAN, 14, Font.BOLD, Color.BLACK);
    private static final Font SUBTITLE = new Font(Font.TIMES_ROMAN, 13, Font.BOLD, Color.BLACK);
    private static final Font CLAUSE_T = new Font(Font.TIMES_ROMAN, 13, Font.BOLD, Color.BLACK);
    private static final Font BODY = new Font(Font.TIMES_ROMAN, 12, Font.NORMAL, Color.BLACK);
    private static final Font BODY_B = new Font(Font.TIMES_ROMAN, 12, Font.BOLD, Color.BLACK);
    private static final Font CELL = new Font(Font.TIMES_ROMAN, 9, Font.NORMAL, Color.BLACK);
    private static final Font CELL_B = new Font(Font.TIMES_ROMAN, 9, Font.BOLD, Color.BLACK);
    private static final Font CELL_W = new Font(Font.TIMES_ROMAN, 9, Font.BOLD, Color.WHITE);
    private static final Font FOOT = new Font(Font.TIMES_ROMAN, 12, Font.NORMAL, GREY);
    public byte[] generate(LetterPdfRequest r) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 50, 50, 45, 88);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            // Watermark behind every page + address/CIN footer on every page.
            writer.setPageEvent(new PageDecorator());
            doc.open();
            String type = r.letterType() == null ? "OFFER" : r.letterType().toUpperCase();
            // Relieving / Experience letter is a distinct, single-page layout.
            if (type.equals("RELIEVING") || type.equals("EXPERIENCE") || type.equals("SERVICE")) {
                addLetterhead(doc);
                addRelievingBody(doc, r);
                doc.close();
                return out.toByteArray();
            }
            boolean appointment = type.equals("APPOINTMENT");
            addLetterhead(doc);
            addDateBlock(doc, r);
            addTitle(doc, appointment ? "APPOINTMENT LETTER" : "OFFER LETTER");
            addRecipient(doc, r, appointment);
            if (appointment) addAppointmentBody(doc, r);
            else addOfferBody(doc, r);
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

    private void addLetterhead(Document doc) throws DocumentException {
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
            Paragraph p = new Paragraph(line, CONTACT);
            p.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(p);
        }
        t.addCell(right);
        doc.add(t);

        // Header blue line REMOVED (per request) — just add spacing before the body.
        Paragraph headSpace = new Paragraph(" ", BODY);
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
        Image sig = loadSignatureFromFile(signatureFileId);
        if (sig == null) sig = loadSignature();
        if (sig != null) {
            sig.scaleToFit(150f, 60f);
            sig.setAlignment(Image.LEFT);
            sig.setSpacingBefore(10f);
            sig.setSpacingAfter(8f);
            doc.add(sig);
        } else {
            Paragraph sigSpace = new Paragraph(" ", BODY);
            sigSpace.setSpacingAfter(30f);
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
        private final Font foot = new Font(Font.TIMES_ROMAN, 12, Font.NORMAL, GREY);

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
                        new Phrase("Address: TechNext Technologies and Services Pvt Ltd, Novel MSR Tech Park,", foot),
                        cx, ruleY - 16f, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                        new Phrase("Marathahalli, Bangalore - 560037 | CIN: U62013KA2026PTC215474", foot),
                        cx, ruleY - 32f, 0);
            } catch (Exception ignored) {}
        }
    }

    private void addDateBlock(Document doc, LetterPdfRequest r) throws DocumentException {
        Paragraph d = new Paragraph("Date: " + safe(r.letterDate()), BODY);
        d.setAlignment(Element.ALIGN_RIGHT);
        doc.add(d);
        Paragraph p = new Paragraph("Place: " + safe(r.place()), BODY);
        p.setAlignment(Element.ALIGN_RIGHT);
        doc.add(p);
    }

    private void addTitle(Document doc, String title) throws DocumentException {
        Paragraph t = new Paragraph(title, TITLE);
        t.setAlignment(Element.ALIGN_CENTER);
        t.setSpacingBefore(12f);
        t.setSpacingAfter(12f);
        doc.add(t);
    }

    private void addRecipient(Document doc, LetterPdfRequest r, boolean appointment) throws DocumentException {
        doc.add(new Paragraph(appointment ? "To," : "To", BODY));
        doc.add(new Paragraph(safe(r.employeeName()), BODY_B));
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

    private void clause(Document doc, int n, String title, String body) throws DocumentException {
        Paragraph t = new Paragraph(n + ". " + title, CLAUSE_T);
        t.setSpacingBefore(8f);
        t.setSpacingAfter(2f);
        doc.add(t);
        Paragraph b = new Paragraph(body, BODY);
        b.setAlignment(Element.ALIGN_JUSTIFIED);
        b.setSpacingAfter(4f);
        doc.add(b);
    }

    private void addOfferBody(Document doc, LetterPdfRequest r) throws DocumentException {
        clause(doc, 1, "Appointment Details", "You are offered the position of " + safe(r.designation()) +
                " on a " + employmentBasisPhrase(r) + ". Your work location shall be " + safe(r.workLocation()) +
                ". Your date of joining will be " + safe(r.dateOfJoining()) + ", and you will report to a person assigned by the Company.");
        clause(doc, 2, "Probation", "You will be on probation for a period of six (6) months from your date of joining. Your performance, conduct, and suitability will be reviewed during this period. Confirmation of employment is not automatic and will be communicated in writing by the Company.");
        clause(doc, 3, "Compensation", "Your Annual Cost to Company (CTC) will be INR " + safe(r.ctcAnnual()) +
                ". The detailed salary structure and breakup are provided in Annexure-A. Statutory deductions such as PF, Professional Tax, and TDS shall apply as per applicable laws. There is no variable pay or joining bonus applicable for this role.");
        clause(doc, 4, "Working Hours and Duties", "Your working hours will be 8-9 hours per day, five days a week. You shall perform all duties assigned to you diligently and comply with all Company policies, rules, and regulations.");
        clause(doc, 5, "Background Verification", "Your employment is subject to successful background verification. Any discrepancy, misrepresentation, or false information identified at any stage may result in immediate termination without notice.");
        clause(doc, 6, "Termination of Employment", "The Company reserves the absolute right to terminate your employment at any time, with or without notice or compensation, during probation or after confirmation, based on performance, misconduct, policy violations, confidentiality breaches, or business requirements. If you resign, you are required to serve 30 days' notice during probation and 60 days' notice after confirmation, or salary in lieu thereof.");
        clause(doc, 7, "Company Assets", "You shall return all Company assets, including laptop, ID card, access credentials, and documents, upon resignation or termination. Failure to return assets may result in deductions or legal recovery.");
        clause(doc, 8, "Confidentiality and Intellectual Property", "You shall maintain strict confidentiality of Company and client information during and after employment. All work, data, and intellectual property created during your employment shall be the sole property of the Company.");
        clause(doc, 9, "Acceptance of Offer", "This offer is valid for five (5) working days from the date of issue. Please sign and return a copy of this letter as a token of acceptance.");
        addSignatory(doc, r);
        addEmployeeAcceptance(doc, r);
    }

    private void addAppointmentBody(Document doc, LetterPdfRequest r) throws DocumentException {
        clause(doc, 1, "Appointment and Role", "You are appointed as a " + employmentTypeNoun(r) + " of the Company in the role of " + safe(r.designation()) +
                " and shall be based at " + safe(r.workLocation()) + ". You will report to such person as may be designated by the Company. Your roles and responsibilities shall be assigned and modified from time to time based on business requirements. You shall devote your full working time, attention, and abilities to the business of the Company and shall not engage in any other employment, assignment, or business activity without prior written consent.");
        clause(doc, 2, "Probation", "You shall be on probation for a period of six (6) months from the date of joining. During this period, your performance, conduct, and suitability for the role will be evaluated. The Company reserves the right to extend, curtail, or terminate the probation period at its sole discretion. Confirmation of your employment shall be communicated in writing and shall not be deemed automatic.");
        clause(doc, 3, "Compensation", "Your annual Cost to Company (CTC) shall be \u20B9" + safe(r.ctcAnnual()) +
                (r.ctcInWords() != null && !r.ctcInWords().isBlank() ? " (" + r.ctcInWords() + ")" : "") +
                ". Your salary shall be paid on a monthly basis and shall be subject to statutory deductions including Provident Fund, Professional Tax, and Income Tax, as applicable under prevailing laws. The detailed salary structure is provided in Annexure-A attached hereto and forms an integral part of this appointment letter. The Company reserves the right to revise or restructure your compensation based on performance, business requirements, or statutory changes.");
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
        Paragraph p = new Paragraph("For TechNext Technologies and Services Private Limited", BODY_B);
        p.setSpacingBefore(12f);
        doc.add(p);
        // signature image if present, else blank space for manual signing
        addSignatureImageOrGap(doc, r.signatureFileId());
        doc.add(new Paragraph("Name: " + safe(r.signatoryName()), BODY));
        doc.add(new Paragraph("Designation: " + safe(r.signatoryTitle()), BODY));
        doc.add(new Paragraph("Date: " + safe(r.letterDate()), BODY));
    }

    /**
     * Employee Acceptance — added directly after the HR Director/signatory
     * block in the Offer Letter. The employee name is the same
     * r.employeeName() value already used throughout the letter (Recipient,
     * Annexure-A, etc.) — never a separate/hardcoded name.
     *
     * Wrapped in a single-cell, borderless PdfPTable with setKeepTogether(true)
     * so OpenPDF keeps the whole section (heading, paragraph, and all three
     * signature/date/place lines) together as one atomic unit — if it doesn't
     * fully fit in the remaining space on the current page, the ENTIRE section
     * moves to the next page rather than splitting awkwardly across pages.
     */
    private void addEmployeeAcceptance(Document doc, LetterPdfRequest r) throws DocumentException {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setKeepTogether(true);
        box.setSpacingBefore(24f);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);

        Paragraph heading = new Paragraph("Employee Acceptance", CLAUSE_T);
        heading.setSpacingAfter(8f);
        cell.addElement(heading);

        Paragraph body = new Paragraph();
        body.setAlignment(Element.ALIGN_JUSTIFIED);
        body.add(new Chunk("I, ", BODY));
        body.add(new Chunk(safe(r.employeeName()), BODY_B));
        body.add(new Chunk(
                ", have read, understood, and accepted the terms and conditions mentioned in this Offer Letter.",
                BODY));
        body.setSpacingAfter(20f);
        cell.addElement(body);

        Paragraph sig = new Paragraph("Signature: ______________________________", BODY);
        sig.setSpacingAfter(10f);
        cell.addElement(sig);

        Paragraph date = new Paragraph("Date: __________________________________", BODY);
        date.setSpacingAfter(10f);
        cell.addElement(date);

        Paragraph place = new Paragraph("Place: __________________________________", BODY);
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
        row(t, "i", "Employee PF (Fixed)", r.pfEmployeeM(), r.pfEmployeeA());
        row(t, "ii", "Professional Tax (KA)", r.ptM(), r.ptA());
        totalRow(t, "Total Deductions (D)", r.deductionsM(), r.deductionsA(), LIGHTBLUE);
        totalRowBlue(t, "Net Take Home (Before TDS)", r.netM(), r.netA());
        sectionRow(t, "D", "Employer Costs Included in CTC");
        row(t, "i", "Employer PF (Fixed)", r.pfEmployerM(), r.pfEmployerA());
        row(t, "ii", "Gratuity (4.81% of Basic)", r.gratuityM(), r.gratuityA());
        row(t, "iii", "Group Health/Accident Insurance", r.insuranceM(), r.insuranceA());
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
    private String cap(String s) { return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private String firstName(String full) {
        if (full == null || full.isBlank()) return "";
        String[] parts = full.trim().split("\\s+");
        return parts.length > 1 ? parts[parts.length - 1] : parts[0];
    }
}