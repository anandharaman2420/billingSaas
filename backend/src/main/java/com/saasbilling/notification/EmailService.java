package com.saasbilling.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Every "send an email" call in the app goes through here. If SMTP
 * isn't configured (SMTP_HOST is blank - the default in dev), this
 * logs what would have been sent and returns normally instead of
 * throwing, so the app remains fully usable without real email
 * credentials. Configure SMTP_HOST/SMTP_USERNAME/SMTP_PASSWORD (see
 * .env.example) to send real emails.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String smtpHost;
    private final String fromAddress;
    private final String fromName;
    private final String frontendBaseUrl;

    public EmailService(JavaMailSender mailSender,
                         @Value("${spring.mail.host:}") String smtpHost,
                         @Value("${app.mail.from-address}") String fromAddress,
                         @Value("${app.mail.from-name}") String fromName,
                         @Value("${app.mail.frontend-base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.smtpHost = smtpHost;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String rawResetToken) {
        String resetLink = frontendBaseUrl + "/reset-password?token=" + rawResetToken;
        String body = "<p>We received a request to reset your password.</p>"
                + "<p><a href=\"" + resetLink + "\">Click here to reset your password</a>. "
                + "This link expires in 30 minutes.</p>"
                + "<p>If you didn't request this, you can safely ignore this email.</p>";
        send(toEmail, "Reset your password", body, null, null);
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName, String businessName) {
        String body = "<p>Hi " + escape(fullName) + ",</p>"
                + "<p>Your account for <strong>" + escape(businessName) + "</strong> is ready. "
                + "You can sign in at <a href=\"" + frontendBaseUrl + "/login\">" + frontendBaseUrl + "/login</a>.</p>";
        send(toEmail, "Welcome to " + businessName, body, null, null);
    }

    /** Spec section 38: "Email invoices" - attaches the generated PDF. */
    @Async
    public void sendInvoiceEmail(String toEmail, String customerName, String invoiceNumber,
                                  BigDecimal grandTotal, byte[] pdfBytes) {
        String body = "<p>Dear " + escape(customerName) + ",</p>"
                + "<p>Please find attached invoice <strong>" + escape(invoiceNumber) + "</strong> "
                + "for <strong>\u20B9" + grandTotal.setScale(2, java.math.RoundingMode.HALF_UP) + "</strong>.</p>"
                + "<p>Thank you for your business.</p>";
        send(toEmail, "Invoice " + invoiceNumber, body, invoiceNumber + ".pdf", pdfBytes);
    }

    /** Spec section 38: "Payment reminders" - sent by the scheduled PaymentReminderJob. */
    @Async
    public void sendPaymentReminderEmail(String toEmail, String customerName, String invoiceNumber,
                                          BigDecimal balanceDue, boolean overdue) {
        String subject = (overdue ? "Overdue: " : "Payment reminder: ") + "Invoice " + invoiceNumber;
        String body = "<p>Dear " + escape(customerName) + ",</p>"
                + "<p>This is a reminder that invoice <strong>" + escape(invoiceNumber) + "</strong> has a balance of "
                + "<strong>\u20B9" + balanceDue.setScale(2, java.math.RoundingMode.HALF_UP) + "</strong> "
                + (overdue ? "which is now overdue." : "coming due soon.") + "</p>"
                + "<p>Please arrange payment at your earliest convenience.</p>";
        send(toEmail, subject, body, null, null);
    }

    // -----------------------------------------------------------------
    private void send(String toEmail, String subject, String htmlBody, String attachmentName, byte[] attachmentBytes) {
        if (smtpHost == null || smtpHost.isBlank()) {
            log.info("SMTP not configured - skipping email. To: {}, Subject: {}", toEmail, subject);
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Skipping email '{}' - no recipient address on file", subject);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, attachmentBytes != null, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            if (attachmentBytes != null && attachmentName != null) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachmentBytes), "application/pdf");
            }

            mailSender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            // Email failures must never break the primary business operation that triggered them.
            log.error("Failed to send email '{}' to {}", subject, toEmail, e);
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
