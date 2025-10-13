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
            boolean hasResend = resendEnabled && resendApiKey != null && !resendApiKey.isEmpty();

            if (!hasSmtp && !hasResend) {
                return Health.down()
                    .withDetail("error", "No email service configured")
                    .withDetail("smtp", "not configured")
                    .withDetail("resend", "not configured")
                    .build();
            }

            health.withDetail("smtp", hasSmtp ? "configured" : "not configured");
            health.withDetail("resend", hasResend ? "configured" : "not configured");

            if (hasSmtp) {
                health.withDetail("smtp.status", "available");
            }
            if (hasResend) {
                health.withDetail("resend.status", "available");
                health.withDetail("resend.api_key_configured", resendApiKey != null && !resendApiKey.isEmpty());
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