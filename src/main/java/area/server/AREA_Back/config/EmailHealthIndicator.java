package area.server.AREA_Back.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health check for email services (SMTP and Resend)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailHealthIndicator implements HealthIndicator {

    @Value("${app.email.resend.enabled:true}")
    private boolean resendEnabled;

    @Value("${app.email.resend.api-key:}")
    private String resendApiKey;

    @Override
    public Health health() {
        try {
            Health.Builder health = Health.up();

            boolean hasSmtp = true;
            boolean hasApiKey = resendApiKey != null && !resendApiKey.isEmpty();
            boolean hasResend = resendEnabled && hasApiKey;

            if (!hasSmtp && !hasResend) {
                return Health.down()
                    .withDetail("error", "No email service configured")
                    .withDetail("smtp", "not configured")
                    .withDetail("resend", "not configured")
                    .build();
            }

            String smtpStatusValue;
            if (hasSmtp) {
                smtpStatusValue = "configured";
            } else {
                smtpStatusValue = "not configured";
            }

            String resendStatusValue;
            if (hasResend) {
                resendStatusValue = "configured";
            } else {
                resendStatusValue = "not configured";
            }

            health.withDetail("smtp", smtpStatusValue);
            health.withDetail("resend", resendStatusValue);

            if (hasSmtp) {
                health.withDetail("smtp.status", "available");
            }
            if (hasResend) {
                health.withDetail("resend.status", "available");
                health.withDetail("resend.api_key_configured", hasApiKey);
            }

            return health.build();

        } catch (Exception e) {
            log.error("Email health check failed", e);
            return Health.down(e)
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}