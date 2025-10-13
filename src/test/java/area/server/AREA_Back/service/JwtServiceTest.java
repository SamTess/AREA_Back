package area.server.AREA_Back.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private MeterRegistry meterRegistry;

    private JwtService jwtService;
    private UUID testUserId;
    private String testEmail;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        jwtService = new JwtService(meterRegistry);

        // Set test values using reflection (since @Value is not available in unit tests)
        ReflectionTestUtils.setField(jwtService, "accessTokenSecret",
                "dGVzdC1hY2Nlc3Mtc2VjcmV0LWZvci1qd3QtdGVzdGluZy0xMjM0NTY3ODkw");
        ReflectionTestUtils.setField(jwtService, "refreshTokenSecret",
                "dGVzdC1yZWZyZXNoLXNlY3JldC1mb3Itand0LXRlc3RpbmctMTIzNDU2Nzg5MA==");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiresIn", "15m");
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiresIn", "7d");

        // Initialize metrics manually since @PostConstruct is not called in unit tests
        jwtService.initMetrics();

        testUserId = UUID.randomUUID();
        testEmail = "test@example.com";
    }

    @Test
    void testGenerateAccessToken() {
        String accessToken = jwtService.generateAccessToken(testUserId, testEmail);

        assertNotNull(accessToken);
        assertFalse(accessToken.isEmpty());
        assertTrue(accessToken.split("\\.").length == 3); // JWT has 3 parts separated by dots
    }

    @Test
    void testGenerateRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(testUserId, testEmail);

        assertNotNull(refreshToken);
        assertFalse(refreshToken.isEmpty());
        assertTrue(refreshToken.split("\\.").length == 3); // JWT has 3 parts separated by dots
    }

    @Test
    void testExtractUserIdFromAccessToken() {
        String accessToken = jwtService.generateAccessToken(testUserId, testEmail);

        UUID extractedUserId = jwtService.extractUserIdFromAccessToken(accessToken);

        assertEquals(testUserId, extractedUserId);
    }

    @Test
    void testExtractUserIdFromRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(testUserId, testEmail);

        UUID extractedUserId = jwtService.extractUserIdFromRefreshToken(refreshToken);

        assertEquals(testUserId, extractedUserId);
    }

    @Test
    void testExtractEmailFromAccessToken() {
        String accessToken = jwtService.generateAccessToken(testUserId, testEmail);

        String extractedEmail = jwtService.extractEmailFromAccessToken(accessToken);

        assertEquals(testEmail, extractedEmail);
    }

    @Test
    void testExtractEmailFromRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(testUserId, testEmail);

        String extractedEmail = jwtService.extractEmailFromRefreshToken(refreshToken);

        assertEquals(testEmail, extractedEmail);
    }

    @Test
    void testIsAccessTokenValid() {
        String accessToken = jwtService.generateAccessToken(testUserId, testEmail);

        assertTrue(jwtService.isAccessTokenValid(accessToken, testUserId));
    }

    @Test
    void testIsAccessTokenValidWithWrongUserId() {
        String accessToken = jwtService.generateAccessToken(testUserId, testEmail);
        UUID wrongUserId = UUID.randomUUID();

        assertFalse(jwtService.isAccessTokenValid(accessToken, wrongUserId));
    }

    @Test
    void testIsRefreshTokenValid() {
        String refreshToken = jwtService.generateRefreshToken(testUserId, testEmail);

        assertTrue(jwtService.isRefreshTokenValid(refreshToken, testUserId));
    }

    @Test
    void testIsRefreshTokenValidWithWrongUserId() {
        String refreshToken = jwtService.generateRefreshToken(testUserId, testEmail);
        UUID wrongUserId = UUID.randomUUID();

        assertFalse(jwtService.isRefreshTokenValid(refreshToken, wrongUserId));
    }

    @Test
    void testIsAccessTokenExpired() {
        String accessToken = jwtService.generateAccessToken(testUserId, testEmail);

        // Fresh token should not be expired
        assertFalse(jwtService.isAccessTokenExpired(accessToken));
    }

    @Test
    void testIsRefreshTokenExpired() {
        String refreshToken = jwtService.generateRefreshToken(testUserId, testEmail);

        // Fresh token should not be expired
        assertFalse(jwtService.isRefreshTokenExpired(refreshToken));
    }

    @Test
    void testGetAccessTokenExpirationMs() {
        long expirationMs = jwtService.getAccessTokenExpirationMs();

        // 15 minutes = 15 * 60 * 1000 ms = 900,000 ms
        assertEquals(Duration.ofMinutes(15).toMillis(), expirationMs);
    }

    @Test
    void testGetRefreshTokenExpirationMs() {
        long expirationMs = jwtService.getRefreshTokenExpirationMs();

        // 7 days = 7 * 24 * 60 * 60 * 1000 ms = 604,800,000 ms
        assertEquals(Duration.ofDays(7).toMillis(), expirationMs);
    }

    @Test
    void testIsAccessTokenValidWithInvalidToken() {
        String invalidToken = "invalid.token.format";

        assertFalse(jwtService.isAccessTokenValid(invalidToken, testUserId));
    }

    @Test
    void testIsRefreshTokenValidWithInvalidToken() {
        String invalidToken = "invalid.token.format";

        assertFalse(jwtService.isRefreshTokenValid(invalidToken, testUserId));
    }

    @Test
    void testExtractUserIdFromAccessTokenWithInvalidToken() {
        String invalidToken = "invalid.token.format";

        assertThrows(Exception.class, () -> {
            jwtService.extractUserIdFromAccessToken(invalidToken);
        });
    }

    @Test
    void testExtractUserIdFromRefreshTokenWithInvalidToken() {
        String invalidToken = "invalid.token.format";

        assertThrows(Exception.class, () -> {
            jwtService.extractUserIdFromRefreshToken(invalidToken);
        });
    }

    @Test
    void testAccessTokenAndRefreshTokenAreDifferent() {
        String accessToken = jwtService.generateAccessToken(testUserId, testEmail);
        String refreshToken = jwtService.generateRefreshToken(testUserId, testEmail);

        assertFalse(accessToken.equals(refreshToken));
    }

    @Test
    void testGeneratedTokensAreUnique() {
        // Use different user IDs to ensure tokens are different
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        String token1 = jwtService.generateAccessToken(userId1, testEmail);
        String token2 = jwtService.generateAccessToken(userId2, testEmail);

        assertFalse(token1.equals(token2));

        // Also test with same user but different emails
        String token3 = jwtService.generateAccessToken(testUserId, testEmail);
        String token4 = jwtService.generateAccessToken(testUserId, "different@example.com");

        assertFalse(token3.equals(token4));
    }

    @Test
    void testParseDurationSeconds() {
        // Use reflection to test the private method
        Duration result = (Duration) ReflectionTestUtils.invokeMethod(jwtService, "parseDuration", "30s");
        assertEquals(Duration.ofSeconds(30), result);
    }

    @Test
    void testParseDurationMinutes() {
        Duration result = (Duration) ReflectionTestUtils.invokeMethod(jwtService, "parseDuration", "15m");
        assertEquals(Duration.ofMinutes(15), result);
    }

    @Test
    void testParseDurationHours() {
        Duration result = (Duration) ReflectionTestUtils.invokeMethod(jwtService, "parseDuration", "2h");
        assertEquals(Duration.ofHours(2), result);
    }

    @Test
    void testParseDurationDays() {
        Duration result = (Duration) ReflectionTestUtils.invokeMethod(jwtService, "parseDuration", "7d");
        assertEquals(Duration.ofDays(7), result);
    }

    @Test
    void testParseDurationWithInvalidUnit() {
        assertThrows(IllegalArgumentException.class, () -> {
            ReflectionTestUtils.invokeMethod(jwtService, "parseDuration", "15x");
        });
    }

    @Test
    void testParseDurationWithInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> {
            ReflectionTestUtils.invokeMethod(jwtService, "parseDuration", "invalid");
        });
    }

    @Test
    void testParseDurationWithNullInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            ReflectionTestUtils.invokeMethod(jwtService, "parseDuration", (String) null);
        });
    }

    @Test
    void testParseDurationWithEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            ReflectionTestUtils.invokeMethod(jwtService, "parseDuration", "");
        });
    }

    @Test
    void testParseDurationWithTooShortInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            ReflectionTestUtils.invokeMethod(jwtService, "parseDuration", "5");
        });
    }

    @Test
    void testParseDurationWithInvalidNumber() {
        assertThrows(IllegalArgumentException.class, () -> {
            ReflectionTestUtils.invokeMethod(jwtService, "parseDuration", "invalidm");
        });
    }

    @Test
    void testTokenContainsCorrectClaims() {
        String accessToken = jwtService.generateAccessToken(testUserId, testEmail);

        UUID extractedUserId = jwtService.extractUserIdFromAccessToken(accessToken);
        String extractedEmail = jwtService.extractEmailFromAccessToken(accessToken);

        assertEquals(testUserId, extractedUserId);
        assertEquals(testEmail, extractedEmail);
    }

    @Test
    void testTokensCannotBeUsedInterchangeably() {
        String accessToken = jwtService.generateAccessToken(testUserId, testEmail);

        // Access token should not be valid as refresh token
        assertThrows(Exception.class, () -> {
            jwtService.extractUserIdFromRefreshToken(accessToken);
        });
    }

    @Test
    void testEmptySecretHandling() {
        // Test with empty secrets - JwtService should generate secure key and not throw exception
        ReflectionTestUtils.setField(jwtService, "accessTokenSecret", "");

        // Should not throw exception, should generate token with secure key
        String token = jwtService.generateAccessToken(testUserId, testEmail);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testNullSecretHandling() {
        // Test with null secrets - JwtService should generate secure key and not throw exception
        ReflectionTestUtils.setField(jwtService, "accessTokenSecret", null);

        // Should not throw exception, should generate token with secure key
        String token = jwtService.generateAccessToken(testUserId, testEmail);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }
}