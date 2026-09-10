package com.technnext.hrms.payroll.service;

import com.technnext.hrms.employee.entity.Employee;
import com.technnext.hrms.employee.repository.EmployeeRepository;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.payroll.entity.PayrollRun;
import com.technnext.hrms.payroll.entity.Payslip;
import com.technnext.hrms.payroll.repository.PayrollRunRepository;
import com.technnext.hrms.payroll.repository.PayslipRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.TextStyle;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exports a processed payroll run as a "salary register" .xlsx — one row per
 * employee with the full PF/PT/TDS breakup plus bank account + IFSC, so
 * Finance can either archive it or hand it to their bank's bulk-upload tool
 * even without the Cashfree integration.
 */
@Service
@RequiredArgsConstructor
public class PayrollExcelExportService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final EmployeeRepository employeeRepository;

    public byte[] exportRun(Integer payrollRunId) {
        PayrollRun run = payrollRunRepository.findById(payrollRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + payrollRunId));
        List<Payslip> payslips = payslipRepository.findByPayrollRunId(payrollRunId);
        Map<java.util.UUID, Employee> employeesById = employeeRepository.findAllById(
                payslips.stream().map(Payslip::getEmployeeId).toList()
        ).stream().collect(Collectors.toMap(Employee::getId, e -> e));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Salary Register");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            int rowIdx = 0;
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.createCell(0).setCellValue(
                    "Salary Register — " + monthName(run.getMonth()) + " " + run.getYear()
                            + " (Status: " + run.getStatus() + ")");
            titleRow.getCell(0).setCellStyle(titleStyle);
            rowIdx++; // blank row

            String[] headers = {
                    "Employee Code", "Employee Name", "Bank Name", "Bank Account Number", "IFSC Code",
                    "PAN Number", "Working Days", "Paid Days", "LOP Days",
                    "Gross Earnings", "PF (Employee)", "PF (Employer)", "Professional Tax", "TDS",
                    "Total Deductions", "Net Pay"
            };
            Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            BigDecimal totalGross = BigDecimal.ZERO, totalPfEmp = BigDecimal.ZERO, totalPfEmpr = BigDecimal.ZERO,
                    totalPt = BigDecimal.ZERO, totalTds = BigDecimal.ZERO, totalDeductions = BigDecimal.ZERO, totalNet = BigDecimal.ZERO;

            for (Payslip p : payslips) {
                Employee e = employeesById.get(p.getEmployeeId());
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(e != null ? e.getEmployeeCode() : "");
                row.createCell(col++).setCellValue(e != null ? (e.getFirstName() + " " + e.getLastName()) : "");
                row.createCell(col++).setCellValue(e != null && e.getBankName() != null ? e.getBankName() : "");
                row.createCell(col++).setCellValue(e != null && e.getBankAccountNumber() != null ? e.getBankAccountNumber() : "");
                row.createCell(col++).setCellValue(e != null && e.getIfscCode() != null ? e.getIfscCode() : "");
                row.createCell(col++).setCellValue(e != null && e.getPanNumber() != null ? e.getPanNumber() : "");
                row.createCell(col++).setCellValue(p.getWorkingDays().doubleValue());
                row.createCell(col++).setCellValue(p.getPaidDays().doubleValue());
                row.createCell(col++).setCellValue(p.getLopDays().doubleValue());
                moneyCell(row, col++, p.getGrossEarnings(), moneyStyle);
                moneyCell(row, col++, p.getPfEmployee(), moneyStyle);
                moneyCell(row, col++, p.getPfEmployer(), moneyStyle);
                moneyCell(row, col++, p.getPtAmount(), moneyStyle);
                moneyCell(row, col++, p.getTdsAmount(), moneyStyle);
                moneyCell(row, col++, p.getTotalDeductions(), moneyStyle);
                moneyCell(row, col, p.getNetPay(), moneyStyle);

                totalGross = totalGross.add(p.getGrossEarnings());
                totalPfEmp = totalPfEmp.add(p.getPfEmployee());
                totalPfEmpr = totalPfEmpr.add(p.getPfEmployer());
                totalPt = totalPt.add(p.getPtAmount());
                totalTds = totalTds.add(p.getTdsAmount());
                totalDeductions = totalDeductions.add(p.getTotalDeductions());
                totalNet = totalNet.add(p.getNetPay());

                if (e != null && (e.getBankAccountNumber() == null || e.getIfscCode() == null)) {
                    Cell flag = row.createCell(headers.length); // extra column, only when needed
                    flag.setCellValue("⚠ Missing bank details — cannot be paid electronically");
                }
            }

            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(8).setCellValue("TOTAL");
            moneyCell(totalRow, 9, totalGross, moneyStyle);
            moneyCell(totalRow, 10, totalPfEmp, moneyStyle);
            moneyCell(totalRow, 11, totalPfEmpr, moneyStyle);
            moneyCell(totalRow, 12, totalPt, moneyStyle);
            moneyCell(totalRow, 13, totalTds, moneyStyle);
            moneyCell(totalRow, 14, totalDeductions, moneyStyle);
            moneyCell(totalRow, 15, totalNet, moneyStyle);
            for (int i = 8; i <= 15; i++) totalRow.getCell(i).setCellStyle(headerStyle);

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate salary register: " + e.getMessage(), e);
        }
    }

    private void moneyCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? 0 : value.doubleValue());
        c.setCellStyle(style);
    }

    private String monthName(int month) {
        return LocalDate.of(2000, month, 1).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}