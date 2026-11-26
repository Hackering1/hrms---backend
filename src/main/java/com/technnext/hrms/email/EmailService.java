package com.technnext.hrms.email;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Locale;

/**
 * Sends transactional emails for the HR portal via the Microsoft Graph API
 * (app-only / client-credentials auth), using the "TechNext HRMS Mailer"
 * Azure AD App Registration and the configured HR / Accounts Payable mailboxes.
 *
 * IMPORTANT reliability rule: sendEmployeeWelcomeEmail/sendEmployeeInviteEmail
 * are NOT allowed to throw. A Graph/Azure AD outage, an expired secret, or a
 * network hiccup must never fail (or roll back) an employee-creation request
 * — it is only logged, so an admin can notice and re-send manually if needed.
 * Callers can treat those two methods as "fire and forget".
 *
 * sendLetterEmail() is the one exception to that rule (see its own doc
 * comment) — it is a direct, user-initiated "Send Email" action, so its
 * caller needs a real success/failure result and it deliberately does throw.
 */
@Service
@Slf4j
public class EmailService {

    private static final String GRAPH_SEND_MAIL_URL_TEMPLATE =
            "https://graph.microsoft.com/v1.0/users/%s/sendMail";

    private final GraphTokenService graphTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    // The mailbox Graph sends AS — must be a real, licensed mailbox covered by
    // the app registration's Mail.Send application permission.
    @Value("${app.mail.from}")
    private String fromMailbox;

    // Formatted employee letters are issued by HR even when other mail flows use a different sender.
    @Value("${app.mail.letter-from:hr@technnext.com}")
    private String letterFromMailbox;

    // Payslips are sent from Accounts Payable; Graph must be authorized for this mailbox.
    @Value("${app.mail.payslip-from:ap@technnext.com}")
    private String payslipFromMailbox;

    @Value("${app.company.name}")
    private String companyName;

    @Value("${app.frontend.login-url}")
    private String loginUrl;

    public EmailService(GraphTokenService graphTokenService) {
        this.graphTokenService = graphTokenService;
    }

    /**
     * Notify a newly-added employee of their Employee ID and (if a login was
     * created for them) their portal credentials.
     */
    public void sendEmployeeWelcomeEmail(EmployeeWelcomeEmailEvent event) {
        if (!mailEnabled) {
            log.info("[EmailService] Mail sending is disabled (app.mail.enabled=false); " +
                    "skipping welcome email to {}", event.toEmail());
            return;
        }
        if (event.toEmail() == null || event.toEmail().isBlank()) {
            log.warn("[EmailService] No recipient email on event for employee {}; skipping.",
                    event.employeeCode());
            return;
        }
        try {
            String subject = "Welcome to " + companyName + " — Your Employee ID: " + event.employeeCode();
            String htmlBody = buildHtmlBody(event);
            sendViaGraph(event.toEmail(), subject, htmlBody);
            log.info("[EmailService] Welcome email sent to {} for employee {}",
                    event.toEmail(), event.employeeCode());
        } catch (Exception ex) {
            // Never let an email failure surface to the caller — employee creation
            // has already succeeded by this point and must not be affected.
            log.error("[EmailService] Failed to send welcome email to {} for employee {}: {}",
                    event.toEmail(), event.employeeCode(), ex.getMessage(), ex);
        }
    }

    private void sendViaGraph(String toEmail, String subject, String htmlBody) throws Exception {
        sendViaGraph(toEmail, subject, htmlBody, null, null);
    }

    /**
     * Same Graph "sendMail" call as above, with an optional single file
     * attachment (used by {@link #sendLetterEmail}). attachmentBytes/Filename
     * are simply omitted from the payload when null/empty, so this overload
     * behaves identically to the original for every existing caller.
     */
    private void sendViaGraph(String toEmail, String subject, String htmlBody,
                               byte[] attachmentBytes, String attachmentFilename) throws Exception {
        sendViaGraph(toEmail, subject, htmlBody, attachmentBytes, attachmentFilename, null, null, null);
    }

    private void sendViaGraph(String toEmail, String subject, String htmlBody,
                               byte[] attachmentBytes, String attachmentFilename,
                               byte[] inlineImageBytes, String inlineImageFilename, String contentId) throws Exception {
        sendViaGraph(toEmail, subject, htmlBody, attachmentBytes, attachmentFilename,
                inlineImageBytes, inlineImageFilename, contentId, fromMailbox);
    }

    private void sendViaGraph(String toEmail, String subject, String htmlBody,
                               byte[] attachmentBytes, String attachmentFilename,
                               byte[] inlineImageBytes, String inlineImageFilename, String contentId,
                               String senderMailbox) throws Exception {
        if (isHrSender(senderMailbox)) {
            htmlBody = appendHrSignature(htmlBody);
            try (var logo = new ClassPathResource("letter-logo.jpg").getInputStream()) {
                inlineImageBytes = logo.readAllBytes();
            }
            inlineImageFilename = "technext-hr-logo.jpg";
            contentId = "technext-hr-logo";
        }

        String accessToken = graphTokenService.getAccessToken();

        ObjectNode emailAddress = objectMapper.createObjectNode();
        emailAddress.put("address", toEmail);
        ObjectNode toRecipientEntry = objectMapper.createObjectNode();
        toRecipientEntry.set("emailAddress", emailAddress);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("contentType", "HTML");
        body.put("content", htmlBody);

        ObjectNode message = objectMapper.createObjectNode();
        message.put("subject", subject);
        message.set("body", body);
        message.putArray("toRecipients").add(toRecipientEntry);

        if (attachmentBytes != null && attachmentBytes.length > 0) {
            ObjectNode attachment = objectMapper.createObjectNode();
            attachment.put("@odata.type", "#microsoft.graph.fileAttachment");
            attachment.put("name", attachmentFilename == null || attachmentFilename.isBlank()
                    ? "attachment.pdf" : attachmentFilename);
            attachment.put("contentType", "application/pdf");
            attachment.put("contentBytes", Base64.getEncoder().encodeToString(attachmentBytes));
            message.put("hasAttachments", true);
            message.putArray("attachments").add(attachment);
        }
        if (inlineImageBytes != null && inlineImageBytes.length > 0) {
            ObjectNode imageAttachment = objectMapper.createObjectNode();
            imageAttachment.put("@odata.type", "#microsoft.graph.fileAttachment");
            imageAttachment.put("name", inlineImageFilename == null || inlineImageFilename.isBlank()
                    ? "technext-logo.jpg" : inlineImageFilename);
            imageAttachment.put("contentType", "image/jpeg");
            imageAttachment.put("contentBytes", Base64.getEncoder().encodeToString(inlineImageBytes));
            imageAttachment.put("isInline", true);
            imageAttachment.put("contentId", contentId == null || contentId.isBlank() ? "technext-logo" : contentId);
            message.put("hasAttachments", true);
            message.withArray("attachments").add(imageAttachment);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("message", message);
        payload.put("saveToSentItems", true);

        String requestUrl = String.format(GRAPH_SEND_MAIL_URL_TEMPLATE, senderMailbox);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Graph's sendMail returns 202 Accepted with an empty body on success.
        if (response.statusCode() != 202) {
            throw new IllegalStateException(
                    "Microsoft Graph sendMail failed: HTTP " + response.statusCode()
                            + " — " + response.body());
        }
    }

    /**
     * Sends a generated letter PDF to the given recipient.
     *
     * UNLIKE sendEmployeeWelcomeEmail/sendEmployeeInviteEmail above, this
     * method is NOT fire-and-forget — it is triggered directly by an HR user
     * clicking "Send Email" on an already-generated letter, so the caller
     * needs an accurate success/failure result to show them. Exceptions are
     * intentionally allowed to propagate (after being logged) rather than
     * swallowed.
     */
    public void sendLetterEmail(String toEmail, String subject, String htmlBody,
                                 byte[] pdfBytes, String pdfFilename) {
        if (!mailEnabled) {
            throw new IllegalStateException(
                    "Email sending is currently disabled for this environment (app.mail.enabled=false).");
        }
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required.");
        }
        try {
            sendViaGraph(toEmail, subject, htmlBody, pdfBytes, pdfFilename,
                    null, null, null, letterFromMailbox);
            log.info("[EmailService] Letter email sent to {}", toEmail);
        } catch (Exception ex) {
            log.error("[EmailService] Failed to send letter email to {}: {}", toEmail, ex.getMessage(), ex);
            throw new IllegalStateException("Failed to send letter email.", ex);
        }
    }

    /** Sends a payslip with the company logo embedded in the email signature. */
    public void sendPayslipEmail(String toEmail, String subject, String htmlBody,
                                  byte[] pdfBytes, String pdfFilename) {
        if (!mailEnabled) {
            throw new IllegalStateException(
                    "Email sending is currently disabled for this environment (app.mail.enabled=false).");
        }
        if (toEmail == null || toEmail.isBlank()) throw new IllegalArgumentException("Recipient email is required.");
        try (var logo = new ClassPathResource("letter-logo.jpg").getInputStream()) {
            sendViaGraph(toEmail, subject, htmlBody, pdfBytes, pdfFilename,
                    logo.readAllBytes(), "technext-logo.jpg", "technext-logo", payslipFromMailbox);
            log.info("[EmailService] Payslip email sent to {}", toEmail);
        } catch (Exception ex) {
            log.error("[EmailService] Failed to send payslip email to {}: {}", toEmail, ex.getMessage(), ex);
            throw new IllegalStateException("Failed to send payslip email.", ex);
        }
    }

    public void sendMissedCheckInReminder(String employeeName, String toEmail, LocalDate date) {
        String displayDate = date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        String body = emailWrapper("<p>Dear " + escape(employeeName) + ",</p>"
                + "<p>This is a reminder that HRMS has not recorded your attendance check-in for "
                + "<strong>" + displayDate + "</strong> as of 9:45 AM.</p>"
                + "<p>If you are working today, please sign in to HRMS and complete your check-in. "
                + "If you believe this reminder is incorrect or you are facing a technical issue, please contact HR.</p>");
        queueHrNotification(toEmail, "Attendance check-in reminder - " + displayDate, body);
    }

    public void sendAttendanceRegularizationStatus(String employeeName, String toEmail,
            LocalDate attendanceDate, LocalTime requestedIn, LocalTime requestedOut,
            String reason, String status, String reviewerRemarks) {
        String displayDate = attendanceDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        String body = emailWrapper("<p>Dear " + escape(employeeName) + ",</p>"
                + "<p>Your attendance regularization request for <strong>" + displayDate
                + "</strong> has a status of <strong>" + escape(status) + "</strong>.</p>"
                + "<p><strong>Requested check-in:</strong> " + escape(formatTime(requestedIn)) + "<br/>"
                + "<strong>Requested check-out:</strong> " + escape(formatTime(requestedOut)) + "<br/>"
                + "<strong>Reason:</strong> " + escape(reason) + "</p>"
                + statusRemarks(reviewerRemarks));
        queueHrNotification(toEmail, "Attendance regularization " + status.toLowerCase(Locale.ROOT)
                + " - " + displayDate, body);
    }

    public void sendLeaveRequestStatus(String employeeName, String toEmail, String leaveType,
            LocalDate fromDate, LocalDate toDate, BigDecimal numberOfDays,
            String status, String reason, String approverRemarks) {
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        String displayRange = fromDate.equals(toDate)
                ? fromDate.format(dateFormat)
                : fromDate.format(dateFormat) + " to " + toDate.format(dateFormat);
        String body = emailWrapper("<p>Dear " + escape(employeeName) + ",</p>"
                + "<p>Your <strong>" + escape(leaveType) + "</strong> leave request for <strong>"
                + displayRange + "</strong> has a status of <strong>" + escape(status) + "</strong>.</p>"
                + "<p><strong>Days:</strong> " + escape(numberOfDays == null ? "" : numberOfDays.stripTrailingZeros().toPlainString())
                + "<br/><strong>Reason:</strong> " + escape(reason) + "</p>"
                + statusRemarks(approverRemarks));
        queueHrNotification(toEmail, "Leave request " + status.toLowerCase(Locale.ROOT)
                + " - " + escapeSubject(leaveType), body);
    }

    private void queueHrNotification(String toEmail, String subject, String htmlBody) {
        Runnable send = () -> {
            if (!mailEnabled) {
                log.info("[EmailService] HR notification email is disabled; skipping email to {}", toEmail);
                return;
            }
            if (toEmail == null || toEmail.isBlank()) {
                log.warn("[EmailService] HR notification has no recipient; skipping.");
                return;
            }
            try {
                sendViaGraph(toEmail, subject, htmlBody, null, null,
                        null, null, null, letterFromMailbox);
                log.info("[EmailService] HR notification sent to {}", toEmail);
            } catch (Exception ex) {
                log.error("[EmailService] Failed to send HR notification to {}: {}", toEmail, ex.getMessage(), ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }

    private String emailWrapper(String content) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:600px;margin:0 auto;color:#1f2937;line-height:1.6;\">"
                + content + "</div>";
    }

    private String statusRemarks(String remarks) {
        return remarks == null || remarks.isBlank() ? ""
                : "<p><strong>Reviewer remarks:</strong> " + escape(remarks) + "</p>";
    }

    private String formatTime(LocalTime value) {
        return value == null ? "Not specified" : value.format(DateTimeFormatter.ofPattern("hh:mm a"));
    }

    private String escapeSubject(String value) {
        return value == null ? "Leave request" : value.replaceAll("[\\r\\n]", " ");
    }

    private boolean isHrSender(String senderMailbox) {
        return senderMailbox != null && (senderMailbox.equalsIgnoreCase(fromMailbox)
                || senderMailbox.equalsIgnoreCase(letterFromMailbox));
    }

    private String appendHrSignature(String htmlBody) {
        String footer = "<div style=\"margin-top:24px;font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#1f2937;\">"
                + "<p style=\"margin:0 0 8px;\">Best Regards,<br/>HR Team</p>"
                + "<img src=\"cid:technext-hr-logo\" alt=\"TechNext logo\" "
                + "style=\"display:block;width:140px;height:auto;margin:8px 0;\"/>"
                + "<div>TechNext Technologies And Services Pvt Ltd</div>"
                + "<div>Email: <a href=\"mailto:hr@technnext.com\">hr@technnext.com</a></div>"
                + "</div>";
        String body = htmlBody == null ? "" : htmlBody;
        int outerClose = body.toLowerCase().lastIndexOf("</div>");
        return outerClose >= 0
                ? body.substring(0, outerClose) + footer + body.substring(outerClose)
                : body + footer;
    }

    private String buildHtmlBody(EmployeeWelcomeEmailEvent event) {
        String fullName = (safe(event.firstName()) + " " + safe(event.lastName())).trim();
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#1f2937;\">");
        sb.append("<h2 style=\"color:#111827;\">Welcome to ").append(escape(companyName)).append("!</h2>");
        sb.append("<p>Dear ").append(escape(fullName)).append(",</p>");
        sb.append("<p>We're excited to have you on board. Your employee record has been created " +
                "in our HR portal with the following details:</p>");
        sb.append("<table style=\"border-collapse:collapse;width:100%;margin:16px 0;\">");
        sb.append(row("Employee ID", event.employeeCode()));
        if (event.designationName() != null && !event.designationName().isBlank()) {
            sb.append(row("Designation", event.designationName()));
        }
        if (event.departmentName() != null && !event.departmentName().isBlank()) {
            sb.append(row("Department", event.departmentName()));
        }
        sb.append("</table>");

        if (event.tempPassword() != null && !event.tempPassword().isBlank()) {
            sb.append("<p>An HR portal login account has also been created for you:</p>");
            sb.append("<table style=\"border-collapse:collapse;width:100%;margin:16px 0;" +
                    "background:#f9fafb;border:1px solid #e5e7eb;border-radius:6px;\">");
            sb.append(row("Login Email", event.loginEmail()));
            sb.append(row("Temporary Password", event.tempPassword()));
            sb.append("</table>");
            sb.append("<p style=\"margin-top:16px;\">")
                    .append("<a href=\"").append(escape(loginUrl)).append("\" ")
                    .append("style=\"background:#2563eb;color:#ffffff;padding:10px 18px;")
                    .append("border-radius:6px;text-decoration:none;display:inline-block;\">")
                    .append("Log in to the HR Portal</a></p>");
            sb.append("<p style=\"color:#6b7280;font-size:13px;\">For security, you will be asked to " +
                    "change this password the first time you log in. Please do not share it with anyone.</p>");
        }

        sb.append("<p>Please quote your Employee ID in all communication with HR. " +
                "Note that your Employee ID cannot be changed by you — only an HR administrator can update it.</p>");
        sb.append("<p>If you have any questions, please reach out to your HR administrator.</p>");

        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Send the "Complete Your Employee Registration" invite email — the entry
     * point into the self-onboarding flow.
     */
    public void sendEmployeeInviteEmail(EmployeeInviteEmailEvent event) {
        if (!mailEnabled) {
            log.info("[EmailService] Mail sending is disabled (app.mail.enabled=false); " +
                    "skipping invite email to {}", event.toEmail());
            return;
        }
        if (event.toEmail() == null || event.toEmail().isBlank()) {
            log.warn("[EmailService] No recipient email on invite event for employee {}; skipping.",
                    event.employeeCode());
            return;
        }
        try {
            String subject = "Complete Your Employee Registration";
            String htmlBody = buildInviteHtmlBody(event);
            sendViaGraph(event.toEmail(), subject, htmlBody);
            log.info("[EmailService] Invite email sent to {} for employee {}",
                    event.toEmail(), event.employeeCode());
        } catch (Exception ex) {
            log.error("[EmailService] Failed to send invite email to {} for employee {}: {}",
                    event.toEmail(), event.employeeCode(), ex.getMessage(), ex);
        }
    }

    private String buildInviteHtmlBody(EmployeeInviteEmailEvent event) {
        String fullName = (safe(event.firstName()) + " " + safe(event.lastName())).trim();
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#1f2937;\">");
        sb.append("<p>Hello ").append(escape(fullName)).append(",</p>");
        sb.append("<p>Welcome to ").append(escape(companyName)).append(".</p>");
        sb.append("<p>Your employee profile has been created.</p>");
        sb.append("<p>Please complete your onboarding using the secure link below.</p>");
        sb.append("<p style=\"margin:24px 0;\">")
                .append("<a href=\"").append(escape(event.onboardingUrl())).append("\" ")
                .append("style=\"background:#2563eb;color:#ffffff;padding:12px 22px;")
                .append("border-radius:6px;text-decoration:none;display:inline-block;font-weight:bold;\">")
                .append("Complete Registration</a></p>");
        sb.append("<p style=\"color:#6b7280;font-size:13px;\">The invitation expires in 24 hours. " +
                "Your Employee ID is ").append(escape(event.employeeCode())).append(".</p>");

        sb.append("</div>");
        return sb.toString();
    }

    private String row(String label, String value) {
        return "<tr>" +
                "<td style=\"padding:6px 12px;font-weight:bold;color:#374151;white-space:nowrap;\">" +
                escape(label) + "</td>" +
                "<td style=\"padding:6px 12px;color:#111827;\">" + escape(value) + "</td>" +
                "</tr>";
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

