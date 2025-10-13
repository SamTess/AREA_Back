package area.server.AREA_Back.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RedisTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private JwtService jwtService;

    private MeterRegistry meterRegistry;

    private RedisTokenService redisTokenService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        redisTokenService = new RedisTokenService(redisTemplate, jwtService, meterRegistry);
        // Initialize metrics manually since @PostConstruct is not called in unit tests
        redisTokenService.initMetrics();
    }

    @Test
    void testStoreAccessToken() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testAccessToken = "test.access.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        // When
        redisTokenService.storeAccessToken(testAccessToken, testUserId);

        // Then
        verify(valueOperations).set(
                eq("access:" + testAccessToken),
                eq(testUserId.toString()),
                eq(Duration.ofMillis(900000L))
        );
    }

    @Test
    void testStoreRefreshToken() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testRefreshToken = "test.refresh.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        // When
        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

        // Then
        verify(valueOperations).set(
                eq("refresh:" + testUserId.toString()),
                eq(testRefreshToken),
                eq(Duration.ofMillis(604800000L))
        );
    }

    @Test
    void testIsAccessTokenValid() {
        // Given
        String testAccessToken = "test.access.token";
        UUID testUserId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());

        // When
        boolean result = redisTokenService.isAccessTokenValid(testAccessToken);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsAccessTokenValidReturnsFalseWhenNull() {
        // Given
        String testAccessToken = "test.access.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("access:" + testAccessToken)).thenReturn(null);

        // When
        boolean result = redisTokenService.isAccessTokenValid(testAccessToken);

        // Then
        assertFalse(result);
    }

    @Test
    void testGetUserIdFromAccessToken() {
        // Given
        String testAccessToken = "test.access.token";
        UUID testUserId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());

        // When
        UUID result = redisTokenService.getUserIdFromAccessToken(testAccessToken);

        // Then
        assertEquals(testUserId, result);
    }

    @Test
    void testGetUserIdFromAccessTokenReturnsNullWhenNotFound() {
        // Given
        String testAccessToken = "test.access.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("access:" + testAccessToken)).thenReturn(null);

        // When
        UUID result = redisTokenService.getUserIdFromAccessToken(testAccessToken);

        // Then
        assertNull(result);
    }

    @Test
    void testDeleteAccessToken() {
        // Given
        String testAccessToken = "test.access.token";

        // When
        redisTokenService.deleteAccessToken(testAccessToken);

        // Then
        verify(redisTemplate).delete("access:" + testAccessToken);
    }

    @Test
    void testDeleteRefreshToken() {
        // Given
        UUID testUserId = UUID.randomUUID();

        // When
        redisTokenService.deleteRefreshToken(testUserId);

        // Then
        verify(redisTemplate).delete("refresh:" + testUserId.toString());
    }

    @Test
    void testGetRefreshToken() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testRefreshToken = "test.refresh.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);

        // When
        String result = redisTokenService.getRefreshToken(testUserId);

        // Then
        assertEquals(testRefreshToken, result);
    }

    @Test
    void testGetRefreshTokenReturnsNullWhenNotFound() {
        // Given
        UUID testUserId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(null);

        // When
        String result = redisTokenService.getRefreshToken(testUserId);

        // Then
        assertNull(result);
    }

    @Test
    void testIsRefreshTokenValid() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testRefreshToken = "test.refresh.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);

        // When
        boolean result = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);

        // Then
        assertTrue(result);
    }

    @Test
    void testIsRefreshTokenValidReturnsFalseWhenTokenDoesNotMatch() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testRefreshToken = "test.refresh.token";
        String storedToken = "different.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(storedToken);

        // When
        boolean result = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);

        // Then
        assertFalse(result);
    }

    @Test
    void testIsRefreshTokenValidReturnsFalseWhenNoStoredToken() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testRefreshToken = "test.refresh.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(null);

        // When
        boolean result = redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken);

        // Then
        assertFalse(result);
    }

    @Test
    void testDeleteAllTokensForUser() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testAccessToken = "test.access.token";

        // When
        redisTokenService.deleteAllTokensForUser(testUserId, testAccessToken);

        // Then
        verify(redisTemplate).delete("access:" + testAccessToken);
        verify(redisTemplate).delete("refresh:" + testUserId.toString());
    }

    @Test
    void testRotateRefreshToken() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String newRefreshToken = "new.refresh.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        // When
        redisTokenService.rotateRefreshToken(testUserId, newRefreshToken);

        // Then
        verify(redisTemplate).delete("refresh:" + testUserId.toString());
        verify(valueOperations).set(
                eq("refresh:" + testUserId.toString()),
                eq(newRefreshToken),
                eq(Duration.ofMillis(604800000L))
        );
    }

    @Test
    void testGetAccessTokenTTL() {
        // Given
        String testAccessToken = "test.access.token";
        Long expectedTTL = 300L;
        when(redisTemplate.getExpire("access:" + testAccessToken)).thenReturn(expectedTTL);

        // When
        Long result = redisTokenService.getAccessTokenTTL(testAccessToken);

        // Then
        assertEquals(expectedTTL, result);
        verify(redisTemplate).getExpire("access:" + testAccessToken);
    }

    @Test
    void testGetRefreshTokenTTL() {
        // Given
        UUID testUserId = UUID.randomUUID();
        Long expectedTTL = 86400L;
        when(redisTemplate.getExpire("refresh:" + testUserId.toString())).thenReturn(expectedTTL);

        // When
        Long result = redisTokenService.getRefreshTokenTTL(testUserId);

        // Then
        assertEquals(expectedTTL, result);
        verify(redisTemplate).getExpire("refresh:" + testUserId.toString());
    }

    @Test
    void testHasActiveRefreshToken() {
        // Given
        UUID testUserId = UUID.randomUUID();
        when(redisTemplate.hasKey("refresh:" + testUserId.toString())).thenReturn(true);

        // When
        boolean result = redisTokenService.hasActiveRefreshToken(testUserId);

        // Then
        assertTrue(result);
        verify(redisTemplate).hasKey("refresh:" + testUserId.toString());
    }

    @Test
    void testHasActiveRefreshTokenReturnsFalseWhenNotExists() {
        // Given
        UUID testUserId = UUID.randomUUID();
        when(redisTemplate.hasKey("refresh:" + testUserId.toString())).thenReturn(false);

        // When
        boolean result = redisTokenService.hasActiveRefreshToken(testUserId);

        // Then
        assertFalse(result);
        verify(redisTemplate).hasKey("refresh:" + testUserId.toString());
    }

    @Test
    void testExtendAccessTokenTTL() {
        // Given
        String testAccessToken = "test.access.token";
        Duration newTTL = Duration.ofHours(2);
        when(redisTemplate.hasKey("access:" + testAccessToken)).thenReturn(true);
        when(redisTemplate.expire("access:" + testAccessToken, newTTL)).thenReturn(true);

        // When
        redisTokenService.extendAccessTokenTTL(testAccessToken, newTTL);

        // Then
        verify(redisTemplate).hasKey("access:" + testAccessToken);
        verify(redisTemplate).expire("access:" + testAccessToken, newTTL);
    }

    @Test
    void testExtendAccessTokenTTLDoesNothingWhenTokenDoesNotExist() {
        // Given
        String testAccessToken = "test.access.token";
        Duration newTTL = Duration.ofHours(2);
        when(redisTemplate.hasKey("access:" + testAccessToken)).thenReturn(false);

        // When
        redisTokenService.extendAccessTokenTTL(testAccessToken, newTTL);

        // Then
        verify(redisTemplate).hasKey("access:" + testAccessToken);
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void testCleanupExpiredTokens() {
        // When
        redisTokenService.cleanupExpiredTokens();

        // Then - This method mainly logs, Redis handles TTL automatically
        // No specific verification needed as it's primarily a logging method
    }

    @Test
    void testGetUserIdFromAccessTokenHandlesInvalidUUID() {
        // Given
        String testAccessToken = "test.access.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("access:" + testAccessToken)).thenReturn("invalid-uuid-string");

        // When
        UUID result = redisTokenService.getUserIdFromAccessToken(testAccessToken);

        // Then
        assertNull(result);
    }
}