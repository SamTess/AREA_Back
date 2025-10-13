package area.server.AREA_Back.service.Auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending emails with SMTP primary and Resend fallback
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final MeterRegistry meterRegistry;
    private final RestTemplate restTemplate;

    @Value("${app.email.from}")
    private String smtpFromEmail;

    @Value("${app.email.resend.api-key}")
    private String resendApiKey;

    @Value("${app.email.resend.from}")
    private String resendFromEmail;

    @Value("${app.email.resend.enabled:true}")
    private boolean resendEnabled;

    private Counter emailSentCounter;
    private Counter emailFailedCounter;
    private Counter smtpEmailSentCounter;
    private Counter resendEmailSentCounter;
    private Counter resendFallbackCounter;
    private Counter verificationEmailSentCounter;
    private Counter passwordResetEmailSentCounter;

    private Timer smtpEmailTimer;
    private Timer resendEmailTimer;

    @PostConstruct
    public void initMetrics() {
        emailSentCounter = Counter.builder("email.sent")
                .description("Total number of emails sent successfully")
                .register(meterRegistry);

        emailFailedCounter = Counter.builder("email.failed")
                .description("Total number of emails that failed to send")
                .register(meterRegistry);

        smtpEmailSentCounter = Counter.builder("email.smtp.sent")
                .description("Total number of emails sent via SMTP")
                .register(meterRegistry);

        resendEmailSentCounter = Counter.builder("email.resend.sent")
                .description("Total number of emails sent via Resend")
                .register(meterRegistry);

        resendFallbackCounter = Counter.builder("email.resend.fallback")
                .description("Total number of emails that fell back to Resend")
                .register(meterRegistry);

        verificationEmailSentCounter = Counter.builder("email.verification.sent")
                .description("Total number of verification emails sent")
                .register(meterRegistry);

        passwordResetEmailSentCounter = Counter.builder("email.password_reset.sent")
                .description("Total number of password reset emails sent")
                .register(meterRegistry);

        smtpEmailTimer = Timer.builder("email.smtp.duration")
                .description("Time taken to send emails via SMTP")
                .register(meterRegistry);

        resendEmailTimer = Timer.builder("email.resend.duration")
                .description("Time taken to send emails via Resend")
                .register(meterRegistry);
    }

    /**
     * Send a simple text email with Resend primary and SMTP fallback
     *
     * @param to      Recipient email address
     * @param subject Email subject
     * @param text    Email body text
     * @return true if email was sent successfully, false otherwise
     */
    public boolean sendEmail(String to, String subject, String text) {
        if (resendEnabled && resendApiKey != null && !resendApiKey.isEmpty()) {
            if (sendEmailViaResend(to, subject, text)) {
                return true;
            }
            log.warn("Resend failed for email to: {}, attempting SMTP fallback", to);
            resendFallbackCounter.increment();
        }

        if (sendEmailViaSmtp(to, subject, text)) {
            return true;
        }

        log.error("Both Resend and SMTP failed for email to: {}", to);
        emailFailedCounter.increment();
        return false;
    }

    /**
     * Send email via SMTP
     */
    private boolean sendEmailViaSmtp(String to, String subject, String text) {
        try {
            return smtpEmailTimer.recordCallable(() -> {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(smtpFromEmail);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(text);

                mailSender.send(message);
                smtpEmailSentCounter.increment();
                emailSentCounter.increment();
                log.info("Email sent successfully via SMTP to: {}", to);
                return true;
            });
        } catch (Exception e) {
            log.warn("SMTP email failed for: {}", to, e);
            return false;
        }
    }

    /**
     * Send email via Resend API
     */
    private boolean sendEmailViaResend(String to, String subject, String text) {
        try {
            return resendEmailTimer.recordCallable(() -> {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(resendApiKey);

                Map<String, Object> emailData = new HashMap<>();
                emailData.put("from", resendFromEmail);
                emailData.put("to", new String[]{to});
                emailData.put("subject", subject);
                emailData.put("text", text);

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailData, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.resend.com/emails",
                    request,
                    String.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    resendEmailSentCounter.increment();
                    emailSentCounter.increment();
                    log.info("Email sent successfully via Resend to: {}", to);
                    return true;
                } else {
                    log.error("Resend API returned error status: {} for email to: {}", response.getStatusCode(), to);
                    return false;
                }
            });
        } catch (Exception e) {
            log.error("Resend email failed for: {}", to, e);
            return false;
        }
    }

    /**
     * Send email verification email
     *
     * @param to          Recipient email address
     * @param subject     Email subject
     * @param verificationUrl The verification URL with token
     * @return true if email was sent successfully, false otherwise
     */
    public boolean sendVerificationEmail(String to, String subject, String verificationUrl) {
        String text = "Welcome to AREA!\n\n"
            + "Please verify your email address by clicking the link below:\n\n"
            + verificationUrl + "\n\n"
            + "This link will expire in 24 hours.\n\n"
            + "If you didn't create an account, please ignore this email.\n\n"
            + "Best regards,\n"
            + "The AREA Team";

        boolean result = sendEmail(to, subject, text);
        if (result) {
            verificationEmailSentCounter.increment();
        }
        return result;
    }

    /**
     * Send password reset email
     *
     * @param to       Recipient email address
     * @param subject  Email subject
     * @param resetUrl The password reset URL with token
     * @return true if email was sent successfully, false otherwise
     */
    public boolean sendPasswordResetEmail(String to, String subject, String resetUrl) {
        String text = "Password Reset Request\n\n"
            + "You requested a password reset for your AREA account.\n\n"
            + "Click the link below to reset your password:\n\n"
            + resetUrl + "\n\n"
            + "This link will expire in 15 minutes.\n\n"
            + "If you didn't request this password reset, please ignore this email.\n\n"
            + "Best regards,\n"
            + "The AREA Team";

        boolean result = sendEmail(to, subject, text);
        if (result) {
            passwordResetEmailSentCounter.increment();
        }
        return result;
    }
}