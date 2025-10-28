package area.server.AREA_Back.worker;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class RetryManagerTest {

    private SimpleMeterRegistry meterRegistry;

    private RetryManager retryManager;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        retryManager = new RetryManager(meterRegistry);
        retryManager.init();
    }

    @Test
    void calculateNextRetryTimeFirstAttempt() {
        // When
        LocalDateTime nextRetry = retryManager.calculateNextRetryTime(0);

        // Then
        assertNotNull(nextRetry);
        assertTrue(nextRetry.isAfter(LocalDateTime.now()));
        assertTrue(nextRetry.isBefore(LocalDateTime.now().plusSeconds(10))); // Should be around 2 seconds + jitter
    }

    @Test
    void calculateNextRetryTimeSecondAttempt() {
        // Given
        LocalDateTime testStart = LocalDateTime.now();

        // When
        LocalDateTime nextRetry = retryManager.calculateNextRetryTime(1);

        // Then
        assertNotNull(nextRetry);
        // With attempt 1: base delay is 4 seconds, jitter can reduce it to ~3.6 seconds minimum
        assertTrue(nextRetry.isAfter(testStart.plusSeconds(3))); // Should be at least 3 seconds from test start
        assertTrue(nextRetry.isBefore(testStart.plusSeconds(6))); // Should be less than 6 seconds from test start
    }

    @Test
    void calculateNextRetryTimeMaxAttempts() {
        // When
        LocalDateTime nextRetry = retryManager.calculateNextRetryTime(5);

        // Then
        assertNull(nextRetry); // No more retries after max attempts
    }

    @Test
    void shouldRetryBelowMaxAttempts() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(2, new RuntimeException("Temporary error"));

        // Then
        assertTrue(shouldRetry);
    }

    @Test
    void shouldRetryMaxAttempts() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(5, new RuntimeException("Any error"));

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void shouldRetryAuthenticationError() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(1, new RuntimeException("Authentication failed"));

        // Then
        assertFalse(shouldRetry); // Authentication errors are not retryable
    }

    @Test
    void shouldRetryValidationError() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(1, new RuntimeException("Invalid request"));

        // Then
        assertFalse(shouldRetry); // Validation errors are not retryable
    }

    @Test
    void shouldRetryNotFoundError() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(1, new RuntimeException("Resource not found"));

        // Then
        assertFalse(shouldRetry); // Not found errors are not retryable
    }

    @Test
    void shouldRetryIllegalArgumentException() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(1, new IllegalArgumentException("Invalid argument"));

        // Then
        assertFalse(shouldRetry); // IllegalArgumentException is not retryable
    }

    @Test
    void shouldRetrySecurityException() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(1, new SecurityException("Access denied"));

        // Then
        assertFalse(shouldRetry); // SecurityException is not retryable
    }

    @Test
    void shouldRetryNetworkError() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(1, new RuntimeException("Connection timeout"));

        // Then
        assertTrue(shouldRetry); // Network errors are retryable
    }

    @Test
    void getRetryStatistics() {
        // When
        RetryManager.RetryStatistics stats = retryManager.getRetryStatistics(2);

        // Then
        assertNotNull(stats);
        assertEquals(2, stats.getCurrentAttempt());
        assertEquals(5, stats.getMaxAttempts()); // Assuming MAX_RETRY_ATTEMPTS = 5
        assertTrue(stats.isWillRetry());
        assertNotNull(stats.getNextRetryTime());
        assertFalse(stats.hasReachedMaxAttempts());
    }

    @Test
    void getRetryStatisticsMaxAttempts() {
        // When
        RetryManager.RetryStatistics stats = retryManager.getRetryStatistics(5);

        // Then
        assertNotNull(stats);
        assertEquals(5, stats.getCurrentAttempt());
        assertEquals(5, stats.getMaxAttempts());
        assertFalse(stats.isWillRetry());
        assertNull(stats.getNextRetryTime());
        assertTrue(stats.hasReachedMaxAttempts());
    }

    @Test
    void shouldNotRetryForAuthorizationError() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(0, new RuntimeException("Authorization error"));

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void shouldNotRetryForInvalidCredentials() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(0, new RuntimeException("Invalid credentials"));

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void shouldNotRetryForAccessDenied() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(0, new RuntimeException("Access denied"));

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void shouldNotRetryForForbidden() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(0, new RuntimeException("Forbidden"));

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void shouldNotRetryForBadRequest() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(0, new RuntimeException("Bad request"));

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void shouldNotRetryForDoesNotExist() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(0, new RuntimeException("User does not exist"));

        // Then
        assertFalse(shouldRetry);
    }

    @Test
    void shouldRetryForNullError() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(0, null);

        // Then
        assertTrue(shouldRetry);
    }

    @Test
    void shouldRetryForErrorWithoutMessage() {
        // When
        boolean shouldRetry = retryManager.shouldRetry(0, new RuntimeException());

        // Then
        assertTrue(shouldRetry);
    }

    @Test
    void shouldHandleUppercaseErrorMessages() {
        // When
        boolean shouldRetry1 = retryManager.shouldRetry(0, new RuntimeException("AUTHENTICATION FAILED"));
        boolean shouldRetry2 = retryManager.shouldRetry(0, new RuntimeException("VALIDATION ERROR"));

        // Then
        assertFalse(shouldRetry1);
        assertFalse(shouldRetry2);
    }

    @Test
    void calculateNextRetryTimeForMultipleAttempts() {
        // When
        LocalDateTime retry0 = retryManager.calculateNextRetryTime(0);
        LocalDateTime retry1 = retryManager.calculateNextRetryTime(1);
        LocalDateTime retry2 = retryManager.calculateNextRetryTime(2);
        LocalDateTime retry3 = retryManager.calculateNextRetryTime(3);
        LocalDateTime retry4 = retryManager.calculateNextRetryTime(4);

        // Then
        assertNotNull(retry0);
        assertNotNull(retry1);
        assertNotNull(retry2);
        assertNotNull(retry3);
        assertNotNull(retry4);
    }

    @Test
    void calculateNextRetryTimeAboveMaxAttempts() {
        // When
        LocalDateTime retry6 = retryManager.calculateNextRetryTime(6);
        LocalDateTime retry10 = retryManager.calculateNextRetryTime(10);

        // Then
        assertNull(retry6);
        assertNull(retry10);
    }
}
