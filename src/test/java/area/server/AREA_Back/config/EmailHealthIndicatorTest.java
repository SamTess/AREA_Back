package area.server.AREA_Back.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EmailHealthIndicatorTest {

    private EmailHealthIndicator emailHealthIndicator;

    @BeforeEach
    void setUp() {
        emailHealthIndicator = new EmailHealthIndicator();
        // Set default values
        ReflectionTestUtils.setField(emailHealthIndicator, "resendEnabled", true);
        ReflectionTestUtils.setField(emailHealthIndicator, "resendApiKey", "test-api-key");
    }

    @Test
    void health_ShouldReturnUp_WhenBothSmtpAndResendConfigured() {
        // Given
        ReflectionTestUtils.setField(emailHealthIndicator, "resendEnabled", true);
        ReflectionTestUtils.setField(emailHealthIndicator, "resendApiKey", "valid-api-key");

        // When
        Health health = emailHealthIndicator.health();

        // Then
        assertEquals(Health.up().build().getStatus(), health.getStatus());
        assertEquals("configured", health.getDetails().get("smtp"));
        assertEquals("configured", health.getDetails().get("resend"));
        assertEquals("available", health.getDetails().get("smtp.status"));
        assertEquals("available", health.getDetails().get("resend.status"));
        assertEquals(true, health.getDetails().get("resend.api_key_configured"));
    }

    @Test
    void health_ShouldReturnUp_WhenOnlySmtpConfigured() {
        // Given
        ReflectionTestUtils.setField(emailHealthIndicator, "resendEnabled", false);
        ReflectionTestUtils.setField(emailHealthIndicator, "resendApiKey", "");

        // When
        Health health = emailHealthIndicator.health();

        // Then
        assertEquals(Health.up().build().getStatus(), health.getStatus());
        assertEquals("configured", health.getDetails().get("smtp"));
        assertEquals("not configured", health.getDetails().get("resend"));
        assertEquals("available", health.getDetails().get("smtp.status"));
        assertNull(health.getDetails().get("resend.status"));
    }

    @Test
    void health_ShouldReturnUp_WhenOnlyResendConfigured() {
        // Given - SMTP is always considered configured in current implementation
        // This test verifies the logic when resend is enabled but SMTP might be considered not configured
        ReflectionTestUtils.setField(emailHealthIndicator, "resendEnabled", true);
        ReflectionTestUtils.setField(emailHealthIndicator, "resendApiKey", "valid-api-key");

        // When
        Health health = emailHealthIndicator.health();

        // Then
        assertEquals(Health.up().build().getStatus(), health.getStatus());
        assertEquals("configured", health.getDetails().get("resend"));
        assertEquals("available", health.getDetails().get("resend.status"));
    }

    @Test
    void health_ShouldHandleResendDisabledGracefully() {
        // Given
        ReflectionTestUtils.setField(emailHealthIndicator, "resendEnabled", false);
        ReflectionTestUtils.setField(emailHealthIndicator, "resendApiKey", "some-key");

        // When
        Health health = emailHealthIndicator.health();

        // Then
        assertEquals(Health.up().build().getStatus(), health.getStatus());
        assertEquals("not configured", health.getDetails().get("resend"));
        assertNull(health.getDetails().get("resend.status"));
    }

    @Test
    void health_ShouldHandleEmptyResendApiKeyGracefully() {
        // Given
        ReflectionTestUtils.setField(emailHealthIndicator, "resendEnabled", true);
        ReflectionTestUtils.setField(emailHealthIndicator, "resendApiKey", "");

        // When
        Health health = emailHealthIndicator.health();

        // Then
        assertEquals(Health.up().build().getStatus(), health.getStatus());
        assertEquals("not configured", health.getDetails().get("resend"));
        assertNull(health.getDetails().get("resend.status"));
    }

    @Test
    void health_ShouldHandleNullResendApiKeyGracefully() {
        // Given
        ReflectionTestUtils.setField(emailHealthIndicator, "resendEnabled", true);
        ReflectionTestUtils.setField(emailHealthIndicator, "resendApiKey", null);

        // When
        Health health = emailHealthIndicator.health();

        // Then
        assertEquals(Health.up().build().getStatus(), health.getStatus());
        assertEquals("not configured", health.getDetails().get("resend"));
        assertNull(health.getDetails().get("resend.status"));
    }
}