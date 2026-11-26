package com.technnext.hrms.suite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.suite.entity.SuiteRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PayslipPdfService {
    private static final String COMPANY = "TechNext Technologies and Services Private Limited";
    private static final String TAGLINE = "Empowering Data Engineers";
    private static final String ADDRESS = "MSR Novel Office Bangalore Karnataka 560037 India";
    private static final Color INK = new Color(0x18, 0x23, 0x35);
    private static final Color MUTED = new Color(0x4B, 0x58, 0x6A);
    private static final Color LINE = new Color(0xD1, 0xD7, 0xDE);
    private static final Color GREEN = new Color(0x12, 0x8B, 0x43);
    private static final Font NORMAL = font(9, false, INK);
    private static final Font MUTED_FONT = font(8, false, MUTED);
    private static final Font BOLD = font(9, true, INK);
    private static final Font SMALL_BOLD = font(8, true, INK);
    private static final Font TITLE = font(13, true, INK);
    private static final Font BIG = font(22, true, INK);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final EmployeeRepository employees;

    private static Font font(float size, boolean bold, Color color) {
        try (InputStream in = new ClassPathResource(bold ? "fonts/Tinos-Bold.ttf" : "fonts/Tinos-Regular.ttf").getInputStream()) {
            BaseFont base = BaseFont.createFont(bold ? "Tinos-Bold.ttf" : "Tinos-Regular.ttf", BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, true, in.readAllBytes(), null);
            return new Font(base, size, Font.NORMAL, color);
        } catch (Exception ex) {
            throw new ExceptionInInitializerError("Cannot load embedded payslip font: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PayslipDocument generate(SuiteRecord payroll) {
        if (payroll == null || !"payroll".equals(payroll.getModule())) throw new BadRequestException("Payroll record was not found.");
        Employee employee = employees.findById(payroll.getEmployeeId())
                .orElseThrow(() -> new BadRequestException("Employee record was not found for this payroll."));
        if (employee.getEmail() == null || !employee.getEmail().matches("^[\\w.+-]+@[\\w-]+\\.[A-Za-z]{2,}$"))
            throw new BadRequestException("A valid employee email address is required before sending the payslip.");
        try {
            JsonNode data = JSON.readTree(payroll.getDetails() == null ? "{}" : payroll.getDetails());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(PageSize.LETTER, 34, 34, 30, 35);
            PdfWriter.getInstance(document, output);
            document.open();
            addHeader(document, payroll.getPeriod());
            addEmployeeSummary(document, employee, payroll, data);
            addCompensation(document, data);
            addNetPay(document, value(data, "netPay"));
            document.add(new Paragraph(" ", MUTED_FONT));
            PdfPTable footer = new PdfPTable(1);
            footer.setWidthPercentage(100);
            PdfPCell words = cell("Amount In Words:   " + amountInWords(value(data, "netPay")), NORMAL);
            words.setPadding(12); words.setBorderColor(LINE); footer.addCell(words);
            document.add(footer);
            Paragraph note = new Paragraph("This is a system-generated document.", MUTED_FONT);
            note.setAlignment(Element.ALIGN_CENTER); note.setSpacingBefore(14); document.add(note);
            document.close();
            String employeeName = fullName(employee);
            String filename = "Payslip_" + safeFile(employee.getEmployeeCode()) + "_" + safeFile(payroll.getPeriod()) + ".pdf";
            return new PayslipDocument(output.toByteArray(), filename, employee.getEmail(), employeeName);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate the payslip PDF.", ex);
        }
    }

    private void addHeader(Document document, String period) throws Exception {
        PdfPTable header = new PdfPTable(new float[]{22, 50, 28});
        header.setWidthPercentage(100);
        PdfPCell logoCell = new PdfPCell(); logoCell.setBorder(Rectangle.NO_BORDER); logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        try (InputStream in = new ClassPathResource("letter-logo.jpg").getInputStream()) {
            Image logo = Image.getInstance(in.readAllBytes()); logo.scaleToFit(100, 58); logoCell.addElement(logo);
        } catch (Exception ignored) { logoCell.addElement(new Paragraph("TECHNEXT", BOLD)); }
        header.addCell(logoCell);
        PdfPCell companyCell = new PdfPCell(); companyCell.setBorder(Rectangle.NO_BORDER); companyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph company = new Paragraph(COMPANY, font(12, true, INK)); company.setAlignment(Element.ALIGN_CENTER); companyCell.addElement(company);
        Paragraph tagline = new Paragraph(TAGLINE, MUTED_FONT); tagline.setAlignment(Element.ALIGN_CENTER); companyCell.addElement(tagline);
        Paragraph address = new Paragraph(ADDRESS, MUTED_FONT); address.setAlignment(Element.ALIGN_CENTER); companyCell.addElement(address);
        header.addCell(companyCell);
        PdfPCell titleCell = new PdfPCell(); titleCell.setBorder(Rectangle.NO_BORDER); titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph label = new Paragraph("Payslip For the Month", BOLD); label.setAlignment(Element.ALIGN_CENTER); titleCell.addElement(label);
        YearMonth month = YearMonth.parse(period);
        Paragraph monthLabel = new Paragraph(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), TITLE);
        monthLabel.setAlignment(Element.ALIGN_CENTER); titleCell.addElement(monthLabel); header.addCell(titleCell);
        document.add(header);
        PdfPTable rule = new PdfPTable(1); rule.setWidthPercentage(100); rule.setSpacingBefore(12); rule.setSpacingAfter(12);
        PdfPCell line = new PdfPCell(new Phrase(" ")); line.setBorder(Rectangle.BOTTOM); line.setBorderColor(LINE); line.setFixedHeight(1); rule.addCell(line); document.add(rule);
    }

    private void addEmployeeSummary(Document document, Employee e, SuiteRecord payroll, JsonNode data) throws DocumentException {
        PdfPTable outer = new PdfPTable(new float[]{66, 34}); outer.setWidthPercentage(100); outer.setSpacingAfter(12);
        PdfPTable summary = new PdfPTable(new float[]{50, 50}); summary.setWidthPercentage(100);
        PdfPCell title = cell("Employee Summary", BOLD); title.setColspan(2); title.setPadding(8); summary.addCell(title);
        addField(summary, "Employee Name", fullName(e)); addField(summary, "Employee ID", e.getEmployeeCode());
        addField(summary, "Designation", e.getDesignation() == null ? "" : e.getDesignation().getName());
        addField(summary, "Department", e.getDepartment() == null ? "" : e.getDepartment().getName());
        addField(summary, "Date of Joining", date(e.getDateOfJoining()));
        addField(summary, "Work Location", e.getBranch() == null ? "" : join(e.getBranch().getCity(), e.getBranch().getState()));
        addField(summary, "Pay Date", YearMonth.parse(payroll.getPeriod()).atEndOfMonth().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        addField(summary, "Bank Name", e.getBankName());
        addField(summary, "Bank Account Number", e.getBankAccountNumber());
        addField(summary, "UAN Number", e.getUanNumber());
        addField(summary, "PAN Number", e.getPanNumber());
        PdfPCell left = new PdfPCell(summary); left.setBorderColor(LINE); left.setPadding(7); outer.addCell(left);
        PdfPTable metrics = new PdfPTable(1); metrics.setWidthPercentage(100);
        metric(metrics, "Paid Days", data.path("paidDays").asText("-"));
        metric(metrics, "LOP Days", data.path("lopDays").asText("-"));
        metric(metrics, "Total Net Pay", rupees(value(data, "netPay")));
        PdfPCell right = new PdfPCell(metrics); right.setBorderColor(GREEN); right.setBorderWidth(1.5f); right.setPadding(6); outer.addCell(right);
        document.add(outer);
    }

    private void addCompensation(Document document, JsonNode data) throws DocumentException {
        PdfPTable columns = new PdfPTable(new float[]{50, 50}); columns.setWidthPercentage(100); columns.setSpacingAfter(10);
        PdfPTable earnings = new PdfPTable(new float[]{68, 32}); earnings.setWidthPercentage(100);
        PdfPTable deductions = new PdfPTable(new float[]{68, 32}); deductions.setWidthPercentage(100);
        addSectionHeader(earnings, "Earnings"); addSectionHeader(deductions, "Deductions");
        addTableHead(earnings); addTableHead(deductions);
        addAmount(earnings, "Basic", value(data, "basic"));
        addAmount(earnings, "House Rent Allowance", value(data, "hra"));
        addAmount(earnings, "Leave Travel Allowance", value(data, "lta"));
        addAmount(earnings, "Fixed Allowance", value(data, "fixedAllowance"));
        addAmount(deductions, "EPF Contribution", value(data, "employeePf"));
        addAmount(deductions, "ESI Contribution", value(data, "employeeEsi"));
        addAmount(deductions, "Professional Tax", value(data, "professionalTax"));
        addAmount(deductions, "TDS", value(data, "tds"));
        addTotal(earnings, "Gross Earnings", value(data, "gross"));
        BigDecimal totalDeductions = value(data, "employeePf").add(value(data, "employeeEsi"))
                .add(value(data, "professionalTax")).add(value(data, "tds"));
        addTotal(deductions, "Total Deductions", totalDeductions);
        PdfPCell left = new PdfPCell(earnings); left.setBorder(Rectangle.NO_BORDER); left.setPaddingRight(10); columns.addCell(left);
        PdfPCell right = new PdfPCell(deductions); right.setBorder(Rectangle.NO_BORDER); right.setPaddingLeft(10); columns.addCell(right);
        document.add(columns);
    }

    private void addNetPay(Document document, BigDecimal net) throws DocumentException {
        PdfPTable table = new PdfPTable(1); table.setWidthPercentage(100);
        PdfPCell c = cell("Total Net Payable = Gross Earnings - Total Deductions = " + rupees(net), BOLD);
        c.setHorizontalAlignment(Element.ALIGN_CENTER); c.setBorder(Rectangle.TOP); c.setBorderColor(new Color(0x99, 0xA3, 0xAF)); c.setPadding(9); table.addCell(c); document.add(table);
    }

    private void addField(PdfPTable table, String label, String value) {
        PdfPCell c = new PdfPCell(); c.setBorder(Rectangle.NO_BORDER); c.setPadding(4);
        c.addElement(new Paragraph(label, MUTED_FONT)); c.addElement(new Paragraph(orDash(value), NORMAL)); table.addCell(c);
    }
    private void metric(PdfPTable table, String label, String value) {
        PdfPCell c = new PdfPCell(); c.setBorder(Rectangle.BOTTOM); c.setBorderColor(new Color(0xB9, 0xF0, 0xC7)); c.setPadding(8);
        Paragraph a = new Paragraph(label, font(8, false, GREEN)); a.setAlignment(Element.ALIGN_CENTER); c.addElement(a);
        Paragraph b = new Paragraph(value, "Total Net Pay".equals(label) ? BIG : font(17, true, INK)); b.setAlignment(Element.ALIGN_CENTER); c.addElement(b); table.addCell(c);
    }
    private void addSectionHeader(PdfPTable table, String value) { PdfPCell c = cell(value, BOLD); c.setColspan(2); c.setPadding(8); c.setBorder(Rectangle.NO_BORDER); table.addCell(c); }
    private void addTableHead(PdfPTable table) {
        PdfPCell a = cell("Description", SMALL_BOLD); a.setBorder(Rectangle.BOTTOM); a.setBorderColor(LINE); a.setPadding(5); table.addCell(a);
        PdfPCell b = cell("AMOUNT", SMALL_BOLD); b.setHorizontalAlignment(Element.ALIGN_RIGHT); b.setBorder(Rectangle.BOTTOM); b.setBorderColor(LINE); b.setPadding(5); table.addCell(b);
    }
    private void addAmount(PdfPTable table, String label, BigDecimal amount) {
        PdfPCell a = cell(label, NORMAL); a.setBorder(Rectangle.NO_BORDER); a.setPadding(6); table.addCell(a);
        PdfPCell b = cell(rupees(amount), NORMAL); b.setHorizontalAlignment(Element.ALIGN_RIGHT); b.setBorder(Rectangle.NO_BORDER); b.setPadding(6); table.addCell(b);
    }
    private void addTotal(PdfPTable table, String label, BigDecimal amount) {
        PdfPCell a = cell(label, BOLD); a.setBorder(Rectangle.TOP); a.setBorderColor(LINE); a.setPadding(7); table.addCell(a);
        PdfPCell b = cell(rupees(amount), BOLD); b.setHorizontalAlignment(Element.ALIGN_RIGHT); b.setBorder(Rectangle.TOP); b.setBorderColor(LINE); b.setPadding(7); table.addCell(b);
    }
    private static PdfPCell cell(String value, Font font) { PdfPCell c = new PdfPCell(new Phrase(orDash(value), font)); c.setBorderColor(LINE); return c; }
    private static String fullName(Employee e) { return String.join(" ", List.of(e.getFirstName(), e.getMiddleName() == null ? "" : e.getMiddleName(), e.getLastName())).replaceAll("\\s+", " ").trim(); }
    private static String date(LocalDate date) { return date == null ? "" : date.format(DateTimeFormatter.ofPattern("d/M/yyyy")); }
    private static String join(String a, String b) { return List.of(a == null ? "" : a, b == null ? "" : b).stream().filter(s -> !s.isBlank()).reduce((x, y) -> x + ", " + y).orElse(""); }
    private static String orDash(String value) { return value == null || value.isBlank() ? "-" : value; }
    private static BigDecimal value(JsonNode node, String key) { return node.hasNonNull(key) ? node.path(key).decimalValue() : BigDecimal.ZERO; }
    private static String rupees(BigDecimal value) { return "₹" + String.format(Locale.ENGLISH, "%,.2f", value); }
    private static String safeFile(String value) { return value == null ? "Employee" : value.replaceAll("[^A-Za-z0-9_-]", "_"); }

    private static String amountInWords(BigDecimal amount) {
        BigDecimal rounded = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        BigInteger rupees = rounded.toBigInteger();
        int paise = rounded.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        String words = indianWords(rupees);
        return "Indian Rupee " + words + (paise > 0 ? " and " + indianWords(BigInteger.valueOf(paise)) + " Paise" : "") + " Only";
    }
    private static String indianWords(BigInteger n) {
        if (n.signum() == 0) return "Zero";
        if (n.compareTo(BigInteger.valueOf(10000000)) >= 0) {
            BigInteger[] q = n.divideAndRemainder(BigInteger.valueOf(10000000));
            return indianWords(q[0]) + " Crore" + (q[1].signum() == 0 ? "" : " " + indianWords(q[1]));
        }
        if (n.compareTo(BigInteger.valueOf(100000)) >= 0) {
            BigInteger[] q = n.divideAndRemainder(BigInteger.valueOf(100000));
            return indianWords(q[0]) + " Lakh" + (q[1].signum() == 0 ? "" : " " + indianWords(q[1]));
        }
        if (n.compareTo(BigInteger.valueOf(1000)) >= 0) {
            BigInteger[] q = n.divideAndRemainder(BigInteger.valueOf(1000));
            return indianWords(q[0]) + " Thousand" + (q[1].signum() == 0 ? "" : " " + indianWords(q[1]));
        }
        int x = n.intValue();
        String[] ones = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        if (x >= 100) return ones[x / 100] + " Hundred" + (x % 100 == 0 ? "" : " " + indianWords(BigInteger.valueOf(x % 100)));
        if (x < 20) return ones[x];
        return tens[x / 10] + (x % 10 == 0 ? "" : " " + ones[x % 10]);
    }

    public record PayslipDocument(byte[] bytes, String filename, String recipientEmail, String employeeName) { }
}
