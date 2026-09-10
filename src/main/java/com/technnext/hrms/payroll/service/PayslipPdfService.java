package com.technnext.hrms.payroll.service;

import com.lowagie.text.*;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.*;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.organization.entity.Branch;
import com.technnext.hrms.payroll.entity.PayrollRun;
import com.technnext.hrms.payroll.entity.Payslip;
import com.technnext.hrms.payroll.entity.PayslipComponent;
import com.technnext.hrms.payroll.repository.PayrollRunRepository;
import com.technnext.hrms.payroll.repository.PayslipComponentRepository;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Generates the branded monthly Payslip PDF (letterhead, employee summary,
 * paid-days/LOP/net-pay highlight box, earnings/deductions table, amount in
 * words) — same visual language and OpenPDF setup as LetterPdfService
 * (letter module), so a payslip and an offer/appointment letter feel like
 * they come from the same system.
 */
@Service
@RequiredArgsConstructor
public class PayslipPdfService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipComponentRepository payslipComponentRepository;
    private final EmployeeRepository employeeRepository;

    private static final Color BLUE = new Color(0x1F, 0x6F, 0xC0);
    private static final Color GREEN_BG = new Color(0xEA, 0xF7, 0xEE);
    private static final Color GREEN_BORDER = new Color(0x34, 0xA8, 0x53);
    private static final Color GREEN_TEXT = new Color(0x1E, 0x7A, 0x36);
    private static final Color GREY = new Color(0x33, 0x33, 0x33);
    private static final Color LABEL_GREY = new Color(0x6B, 0x72, 0x80);
    private static final Color RULE_GREY = new Color(0xE2, 0xE5, 0xEA);

    private static final Font H_LOGO = new Font(Font.TIMES_ROMAN, 18, Font.BOLD, BLUE);
    private static final Font COMPANY_NAME = new Font(Font.TIMES_ROMAN, 15, Font.BOLD, Color.BLACK);
    private static final Font TAGLINE = new Font(Font.TIMES_ROMAN, 9, Font.NORMAL, LABEL_GREY);
    private static final Font ADDRESS = new Font(Font.TIMES_ROMAN, 9, Font.NORMAL, LABEL_GREY);
    private static final Font PAYSLIP_TITLE = new Font(Font.TIMES_ROMAN, 13, Font.BOLD, Color.BLACK);
    private static final Font PAYSLIP_MONTH = new Font(Font.TIMES_ROMAN, 13, Font.BOLD, Color.BLACK);
    private static final Font SECTION_HEAD = new Font(Font.TIMES_ROMAN, 11, Font.BOLD, Color.BLACK);
    private static final Font LABEL = new Font(Font.TIMES_ROMAN, 9, Font.NORMAL, LABEL_GREY);
    private static final Font VALUE = new Font(Font.TIMES_ROMAN, 10, Font.BOLD, Color.BLACK);
    private static final Font BOX_LABEL = new Font(Font.TIMES_ROMAN, 9, Font.NORMAL, GREEN_TEXT);
    private static final Font BOX_VALUE = new Font(Font.TIMES_ROMAN, 20, Font.BOLD, GREEN_TEXT);
    private static final Font BOX_NET_LABEL = new Font(Font.TIMES_ROMAN, 10, Font.NORMAL, GREEN_TEXT);
    private static final Font BOX_NET_VALUE = new Font(Font.TIMES_ROMAN, 22, Font.BOLD, GREEN_TEXT);
    private static final Font TABLE_HEAD = new Font(Font.TIMES_ROMAN, 9, Font.BOLD, LABEL_GREY);
    private static final Font TABLE_CELL = new Font(Font.TIMES_ROMAN, 10, Font.NORMAL, Color.BLACK);
    private static final Font TABLE_TOTAL = new Font(Font.TIMES_ROMAN, 10, Font.BOLD, Color.BLACK);
    private static final Font FORMULA = new Font(Font.TIMES_ROMAN, 10, Font.BOLD, Color.BLACK);
    private static final Font WORDS_LABEL = new Font(Font.TIMES_ROMAN, 9, Font.BOLD, Color.BLACK);
    private static final Font WORDS_VALUE = new Font(Font.TIMES_ROMAN, 10, Font.NORMAL, Color.BLACK);
    private static final Font FOOT = new Font(Font.TIMES_ROMAN, 8, Font.ITALIC, LABEL_GREY);

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generate(Payslip payslip) {
        PayrollRun run = payrollRunRepository.findById(payslip.getPayrollRunId())
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + payslip.getPayrollRunId()));
        Employee employee = employeeRepository.findById(payslip.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + payslip.getEmployeeId()));
        List<PayslipComponent> lines = payslipComponentRepository.findByPayslipIdOrderByDisplayOrder(payslip.getId());

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 30, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            addHeader(doc, run);
            addSummaryAndHighlightRow(doc, payslip, employee);
            addEarningsAndDeductions(doc, payslip, lines);
            addTotalFormula(doc, payslip);
            addAmountInWords(doc, payslip);
            addFooterNote(doc);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate payslip PDF: " + e.getMessage(), e);
        }
    }

    private void addHeader(Document doc, PayrollRun run) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.6f, 1f});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setVerticalAlignment(Element.ALIGN_TOP);

        PdfPTable inner = new PdfPTable(2);
        inner.setWidths(new float[]{1f, 4f});
        Image logoImg = loadImage("letter-logo.png", "letter-logo.jpg", "logo.png", "logo.jpg");
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (logoImg != null) {
            logoImg.scaleToFit(55f, 55f);
            logoCell.addElement(logoImg);
        } else {
            logoCell.addElement(new Paragraph("TN", H_LOGO));
        }
        inner.addCell(logoCell);

        PdfPCell nameCell = new PdfPCell();
        nameCell.setBorder(Rectangle.NO_BORDER);
        nameCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        nameCell.addElement(new Paragraph("TechNext Technologies and", COMPANY_NAME));
        nameCell.addElement(new Paragraph("Services Private Limited", COMPANY_NAME));
        Paragraph tag = new Paragraph("Empowering Data Engineers", TAGLINE);
        tag.setSpacingBefore(2f);
        nameCell.addElement(tag);
        inner.addCell(nameCell);
        left.addElement(inner);

        Paragraph addr = new Paragraph("MSR Novel Office Bangalore Karnataka 560037 India", ADDRESS);
        addr.setAlignment(Element.ALIGN_CENTER);
        addr.setSpacingBefore(4f);
        left.addElement(addr);
        t.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setVerticalAlignment(Element.ALIGN_TOP);
        Paragraph p1 = new Paragraph("Payslip For the Month", PAYSLIP_TITLE);
        p1.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(p1);
        Paragraph p2 = new Paragraph(monthYearLabel(run.getMonth(), run.getYear()), PAYSLIP_MONTH);
        p2.setAlignment(Element.ALIGN_RIGHT);
        p2.setSpacingBefore(2f);
        right.addElement(p2);
        t.addCell(right);

        doc.add(t);

        PdfPTable ruleTable = new PdfPTable(1);
        ruleTable.setWidthPercentage(100);
        ruleTable.setSpacingBefore(8f);
        PdfPCell ruleCell = new PdfPCell();
        ruleCell.setBorder(Rectangle.BOTTOM);
        ruleCell.setBorderColor(RULE_GREY);
        ruleCell.setBorderWidthBottom(1.5f);
        ruleCell.setFixedHeight(4f);
        ruleTable.addCell(ruleCell);
        doc.add(ruleTable);
    }

    private void addSummaryAndHighlightRow(Document doc, Payslip payslip, Employee employee) throws DocumentException {
        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setWidths(new float[]{2.1f, 1f});
        outer.setSpacingBefore(14f);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0f);

        Paragraph heading = new Paragraph("Employee Summary", SECTION_HEAD);
        heading.setSpacingAfter(8f);
        left.addElement(heading);

        String workLocation = "\u2014";
        if (employee.getBranch() != null) {
            Branch b = employee.getBranch();
            workLocation = b.getCity() != null ? b.getCity() : "\u2014";
        }
        String designation = employee.getDesignation() != null ? employee.getDesignation().getName() : "\u2014";
        String department = employee.getDepartment() != null ? employee.getDepartment().getName() : "\u2014";

        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        summaryField(grid, "Employee Name", fullName(employee));
        summaryField(grid, "Employee ID", employee.getEmployeeCode());
        summaryField(grid, "Designation", designation);
        summaryField(grid, "Department", department);
        summaryField(grid, "Date of Joining", employee.getDateOfJoining() != null ? DMY.format(employee.getDateOfJoining()) : "\u2014");
        summaryField(grid, "Work Location", workLocation);
        summaryField(grid, "Pay Date", LocalDate.now().format(DMY));
        summaryField(grid, "Bank Name", nullSafe(employee.getBankName()));
        summaryField(grid, "Bank Account Number", nullSafe(employee.getBankAccountNumber()));
        summaryField(grid, "UAN Number", nullSafe(employee.getUanNumber()));
        summaryField(grid, "PAN Number", nullSafe(employee.getPanNumber()));
        summaryField(grid, "", "");
        left.addElement(grid);

        outer.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBackgroundColor(GREEN_BG);
        right.setBorderColor(GREEN_BORDER);
        right.setBorderWidth(1f);
        right.setPadding(14f);

        addBoxStat(right, "Paid Days", stripTrailingZero(payslip.getPaidDays()), false);
        addBoxDivider(right);
        addBoxStat(right, "LOP Days", stripTrailingZero(payslip.getLopDays()), false);
        addBoxDivider(right);
        addBoxStat(right, "Total Net Pay", "\u20B9" + formatMoney(payslip.getNetPay()), true);

        outer.addCell(right);
        doc.add(outer);
    }

    private void summaryField(PdfPTable grid, String label, String value) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingBottom(8f);
        c.setPaddingRight(10f);
        if (!label.isEmpty()) {
            Paragraph l = new Paragraph(label, LABEL);
            l.setSpacingAfter(1f);
            c.addElement(l);
            c.addElement(new Paragraph(value, VALUE));
        }
        grid.addCell(c);
    }

    private void addBoxStat(PdfPCell box, String label, String value, boolean big) {
        Paragraph l = new Paragraph(label, big ? BOX_NET_LABEL : BOX_LABEL);
        l.setAlignment(Element.ALIGN_CENTER);
        box.addElement(l);
        Paragraph v = new Paragraph(value, big ? BOX_NET_VALUE : BOX_VALUE);
        v.setAlignment(Element.ALIGN_CENTER);
        v.setSpacingBefore(2f);
        v.setSpacingAfter(6f);
        box.addElement(v);
    }

    private void addBoxDivider(PdfPCell box) {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(GREEN_BORDER);
        c.setBorderWidthBottom(0.5f);
        c.setFixedHeight(2f);
        rule.addCell(c);
        box.addElement(rule);
    }

    private void addEarningsAndDeductions(Document doc, Payslip payslip, List<PayslipComponent> lines) throws DocumentException {
        List<PayslipComponent> earnings = lines.stream().filter(l -> "EARNING".equals(l.getComponentType())).toList();
        List<PayslipComponent> deductions = lines.stream().filter(l -> "DEDUCTION".equals(l.getComponentType())).toList();

        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setSpacingBefore(16f);
        outer.setWidths(new float[]{1f, 1f});

        outer.addCell(earningsDeductionsColumn("Earnings", earnings, "Gross Earnings", payslip.getGrossEarnings()));
        outer.addCell(earningsDeductionsColumn("Deductions", deductions, "Total Deductions", payslip.getTotalDeductions()));

        doc.add(outer);
    }

    private PdfPCell earningsDeductionsColumn(String title, List<PayslipComponent> lines, String totalLabel, BigDecimal total) {
        PdfPCell col = new PdfPCell();
        col.setBorder(Rectangle.NO_BORDER);
        col.setPadding(4f);

        Paragraph h = new Paragraph(title, SECTION_HEAD);
        h.setSpacingAfter(4f);
        col.addElement(h);

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{2.2f, 1f});

        PdfPCell descHead = new PdfPCell(new Phrase("Description", TABLE_HEAD));
        descHead.setBorder(Rectangle.BOTTOM);
        descHead.setBorderColor(RULE_GREY);
        descHead.setPaddingBottom(4f);
        t.addCell(descHead);
        PdfPCell amtHead = new PdfPCell(new Phrase("AMOUNT", TABLE_HEAD));
        amtHead.setBorder(Rectangle.BOTTOM);
        amtHead.setBorderColor(RULE_GREY);
        amtHead.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amtHead.setPaddingBottom(4f);
        t.addCell(amtHead);

        for (PayslipComponent line : lines) {
            PdfPCell d = new PdfPCell(new Phrase(line.getComponentName(), TABLE_CELL));
            d.setBorder(Rectangle.NO_BORDER);
            d.setPadding(4f);
            t.addCell(d);
            PdfPCell a = new PdfPCell(new Phrase("\u20B9" + formatMoney(line.getAmount()), TABLE_CELL));
            a.setBorder(Rectangle.NO_BORDER);
            a.setHorizontalAlignment(Element.ALIGN_RIGHT);
            a.setPadding(4f);
            t.addCell(a);
        }
        if (lines.isEmpty()) {
            PdfPCell none = new PdfPCell(new Phrase("\u2014", TABLE_CELL));
            none.setBorder(Rectangle.NO_BORDER);
            none.setPadding(4f);
            t.addCell(none);
            PdfPCell noneAmt = new PdfPCell(new Phrase("\u20B90.00", TABLE_CELL));
            noneAmt.setBorder(Rectangle.NO_BORDER);
            noneAmt.setHorizontalAlignment(Element.ALIGN_RIGHT);
            noneAmt.setPadding(4f);
            t.addCell(noneAmt);
        }

        PdfPCell totalLabelCell = new PdfPCell(new Phrase(totalLabel, TABLE_TOTAL));
        totalLabelCell.setBorder(Rectangle.TOP);
        totalLabelCell.setBorderColor(RULE_GREY);
        totalLabelCell.setPaddingTop(4f);
        t.addCell(totalLabelCell);
        PdfPCell totalAmtCell = new PdfPCell(new Phrase("\u20B9" + formatMoney(total), TABLE_TOTAL));
        totalAmtCell.setBorder(Rectangle.TOP);
        totalAmtCell.setBorderColor(RULE_GREY);
        totalAmtCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalAmtCell.setPaddingTop(4f);
        t.addCell(totalAmtCell);

        col.addElement(t);
        return col;
    }

    private void addTotalFormula(Document doc, Payslip payslip) throws DocumentException {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingBefore(10f);
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(new Color(0xF7, 0xF8, 0xFA));
        c.setBorder(Rectangle.BOX);
        c.setBorderColor(RULE_GREY);
        c.setPadding(8f);
        Paragraph p = new Paragraph(
                "Total Net Payable = Gross Earnings - Total Deductions = \u20B9" + formatMoney(payslip.getNetPay()), FORMULA);
        p.setAlignment(Element.ALIGN_CENTER);
        c.addElement(p);
        box.addCell(c);
        doc.add(box);
    }

    private void addAmountInWords(Document doc, Payslip payslip) throws DocumentException {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingBefore(10f);
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(new Color(0xF7, 0xF8, 0xFA));
        c.setBorder(Rectangle.BOX);
        c.setBorderColor(RULE_GREY);
        c.setPadding(8f);
        Paragraph p = new Paragraph();
        p.add(new Chunk("Amount In Words : ", WORDS_LABEL));
        p.add(new Chunk(AmountInWordsConverter.toWords(payslip.getNetPay()), WORDS_VALUE));
        c.addElement(p);
        box.addCell(c);
        doc.add(box);
    }

    private void addFooterNote(Document doc) throws DocumentException {
        Paragraph rule = new Paragraph(" ");
        rule.setSpacingBefore(16f);
        doc.add(rule);
        Paragraph p = new Paragraph("This is a system-generated document.", FOOT);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    private Image loadImage(String... candidates) {
        for (String name : candidates) {
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

    private String monthYearLabel(int month, int year) {
        LocalDate d = LocalDate.of(year, month, 1);
        return d.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
    }

    private String fullName(Employee e) {
        String first = e.getFirstName() == null ? "" : e.getFirstName();
        String last = e.getLastName() == null ? "" : e.getLastName();
        return (first + " " + last).trim();
    }

    private String nullSafe(String s) {
        return s == null || s.isBlank() ? "\u2014" : s;
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return String.format(Locale.ENGLISH, "%,.2f", amount);
    }

    private String stripTrailingZero(BigDecimal n) {
        if (n == null) return "0";
        return n.stripTrailingZeros().toPlainString();
    }
}