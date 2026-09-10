package com.technnext.hrms.email;

/**
 * Raised once a payroll run is marked PAID, one event per employee. Carries a
 * plain snapshot (never the Payslip/Employee JPA entities themselves) plus
 * the already-rendered PDF bytes, so the listener — which runs on a separate
 * thread after the DB transaction has committed — never touches a
 * detached/lazy-loaded entity.
 *
 * @param toEmail       employee's email address (event only published when present)
 * @param employeeName  full name, for the greeting
 * @param employeeCode  Employee ID
 * @param monthLabel    e.g. "August"
 * @param year          e.g. 2026
 * @param netPayFormatted already-formatted amount (e.g. "85,000.00") for the email body
 * @param pdfBytes      the rendered payslip PDF, ready to attach
 */
public record PayslipEmailEvent(
        String toEmail,
        String employeeName,
        String employeeCode,
        String monthLabel,
        int year,
        String netPayFormatted,
        byte[] pdfBytes
) {
}