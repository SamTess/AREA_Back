package area.server.AREA_Back.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SetOperations;

import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private SetOperations<String, Object> setOperations;

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

    // ===== Tests for JTI-related methods =====

    @Test
    void testStoreAccessTokenWithJti() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testAccessToken = "test.access.token";
        String testJti = "test-jti-123";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtService.extractJtiFromAccessToken(testAccessToken)).thenReturn(testJti);

        // When
        redisTokenService.storeAccessToken(testAccessToken, testUserId);

        // Then
        verify(valueOperations).set(
                eq("access:" + testAccessToken),
                eq(testUserId.toString()),
                eq(Duration.ofMillis(900000L))
        );
        verify(valueOperations).set(
                eq("access:jti:" + testJti),
                eq(testUserId.toString()),
                eq(Duration.ofMillis(900000L))
        );
        verify(setOperations).add(eq("user:tokens:" + testUserId.toString()), eq(testJti));
        verify(redisTemplate).expire(eq("user:tokens:" + testUserId.toString()), eq(Duration.ofMillis(900000L)));
    }

    @Test
    void testStoreAccessTokenWithJtiException() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testAccessToken = "test.access.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtService.extractJtiFromAccessToken(testAccessToken)).thenThrow(new RuntimeException("JTI extraction failed"));

        // When
        redisTokenService.storeAccessToken(testAccessToken, testUserId);

        // Then
        verify(valueOperations).set(
                eq("access:" + testAccessToken),
                eq(testUserId.toString()),
                eq(Duration.ofMillis(900000L))
        );
        // JTI operations should not be called due to exception
    }

    @Test
    void testStoreRefreshTokenWithJti() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testRefreshToken = "test.refresh.token";
        String testJti = "refresh-jti-456";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtService.extractJtiFromRefreshToken(testRefreshToken)).thenReturn(testJti);

        // When
        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

        // Then
        verify(valueOperations).set(
                eq("refresh:" + testUserId.toString()),
                eq(testRefreshToken),
                eq(Duration.ofMillis(604800000L))
        );
        verify(valueOperations).set(
                eq("refresh:jti:" + testJti),
                eq(testUserId.toString()),
                eq(Duration.ofMillis(604800000L))
        );
    }

    @Test
    void testStoreRefreshTokenWithJtiException() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testRefreshToken = "test.refresh.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtService.extractJtiFromRefreshToken(testRefreshToken)).thenThrow(new RuntimeException("JTI extraction failed"));

        // When
        redisTokenService.storeRefreshToken(testUserId, testRefreshToken);

        // Then
        verify(valueOperations).set(
                eq("refresh:" + testUserId.toString()),
                eq(testRefreshToken),
                eq(Duration.ofMillis(604800000L))
        );
        // JTI operations should not be called due to exception
    }

    @Test
    void testDeleteAccessTokenWithJti() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testAccessToken = "test.access.token";
        String testJti = "test-jti-123";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(jwtService.extractJtiFromAccessToken(testAccessToken)).thenReturn(testJti);
        when(valueOperations.get("access:" + testAccessToken)).thenReturn(testUserId.toString());

        // When
        redisTokenService.deleteAccessToken(testAccessToken);

        // Then
        verify(redisTemplate).delete("access:" + testAccessToken);
        verify(redisTemplate).delete("access:jti:" + testJti);
        verify(setOperations).remove("user:tokens:" + testUserId.toString(), testJti);
    }

    @Test
    void testDeleteAccessTokenWithJtiException() {
        // Given
        String testAccessToken = "test.access.token";
        when(jwtService.extractJtiFromAccessToken(testAccessToken)).thenThrow(new RuntimeException("JTI extraction failed"));

        // When
        redisTokenService.deleteAccessToken(testAccessToken);

        // Then
        verify(redisTemplate).delete("access:" + testAccessToken);
        // JTI cleanup should fail gracefully
    }

    @Test
    void testDeleteRefreshTokenWithJti() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testRefreshToken = "test.refresh.token";
        String testJti = "refresh-jti-456";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);
        when(jwtService.extractJtiFromRefreshToken(testRefreshToken)).thenReturn(testJti);

        // When
        redisTokenService.deleteRefreshToken(testUserId);

        // Then
        verify(redisTemplate).delete("refresh:" + testUserId.toString());
        verify(redisTemplate).delete("refresh:jti:" + testJti);
    }

    @Test
    void testDeleteRefreshTokenWithJtiException() {
        // Given
        UUID testUserId = UUID.randomUUID();
        String testRefreshToken = "test.refresh.token";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:" + testUserId.toString())).thenReturn(testRefreshToken);
        when(jwtService.extractJtiFromRefreshToken(testRefreshToken)).thenThrow(new RuntimeException("JTI extraction failed"));

        // When
        redisTokenService.deleteRefreshToken(testUserId);

        // Then
        verify(redisTemplate).delete("refresh:" + testUserId.toString());
        // JTI cleanup should fail gracefully
    }

    @Test
    void testRevokeAccessTokenByJti() {
        // Given
        String testJti = "test-jti-123";
        UUID testUserId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get("access:jti:" + testJti)).thenReturn(testUserId.toString());

        // When
        redisTokenService.revokeAccessTokenByJti(testJti);

        // Then
        verify(setOperations).remove("user:tokens:" + testUserId.toString(), testJti);
        verify(redisTemplate).delete("access:jti:" + testJti);
    }

    @Test
    void testRevokeAccessTokenByJtiWithNullUserId() {
        // Given
        String testJti = "test-jti-123";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("access:jti:" + testJti)).thenReturn(null);

        // When
        redisTokenService.revokeAccessTokenByJti(testJti);

        // Then
        verify(redisTemplate).delete("access:jti:" + testJti);
        verify(redisTemplate, never()).opsForSet();
    }

    @Test
    void testRevokeAccessTokenByJtiWithInvalidUUID() {
        // Given
        String testJti = "test-jti-123";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("access:jti:" + testJti)).thenReturn("invalid-uuid");

        // When
        redisTokenService.revokeAccessTokenByJti(testJti);

        // Then
        verify(redisTemplate).delete("access:jti:" + testJti);
    }

    @Test
    void testRevokeRefreshTokenByJti() {
        // Given
        String testJti = "refresh-jti-456";

        // When
        redisTokenService.revokeRefreshTokenByJti(testJti);

        // Then
        verify(redisTemplate).delete("refresh:jti:" + testJti);
    }

    @Test
    void testGetUserActiveTokenJtis() {
        // Given
        UUID testUserId = UUID.randomUUID();
        Set<Object> expectedJtis = new HashSet<>();
        expectedJtis.add("jti-1");
        expectedJtis.add("jti-2");
        expectedJtis.add("jti-3");
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("user:tokens:" + testUserId.toString())).thenReturn(expectedJtis);

        // When
        Set<Object> result = redisTokenService.getUserActiveTokenJtis(testUserId);

        // Then
        assertEquals(expectedJtis, result);
        assertEquals(3, result.size());
        verify(setOperations).members("user:tokens:" + testUserId.toString());
    }

    @Test
    void testGetUserActiveTokenJtisReturnsEmptySet() {
        // Given
        UUID testUserId = UUID.randomUUID();
        Set<Object> emptySet = new HashSet<>();
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("user:tokens:" + testUserId.toString())).thenReturn(emptySet);

        // When
        Set<Object> result = redisTokenService.getUserActiveTokenJtis(testUserId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testRevokeAllUserAccessTokens() {
        // Given
        UUID testUserId = UUID.randomUUID();
        Set<Object> jtis = new HashSet<>();
        jtis.add("jti-1");
        jtis.add("jti-2");
        jtis.add("jti-3");
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("user:tokens:" + testUserId.toString())).thenReturn(jtis);

        // When
        redisTokenService.revokeAllUserAccessTokens(testUserId);

        // Then
        verify(redisTemplate).delete("access:jti:jti-1");
        verify(redisTemplate).delete("access:jti:jti-2");
        verify(redisTemplate).delete("access:jti:jti-3");
        verify(redisTemplate).delete("user:tokens:" + testUserId.toString());
    }

    @Test
    void testRevokeAllUserAccessTokensWithNullJtis() {
        // Given
        UUID testUserId = UUID.randomUUID();
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("user:tokens:" + testUserId.toString())).thenReturn(null);

        // When
        redisTokenService.revokeAllUserAccessTokens(testUserId);

        // Then
        String userTokensKey = "user:tokens:" + testUserId.toString();
        verify(redisTemplate, never()).delete(eq(userTokensKey));
    }

    @Test
    void testRevokeAllUserAccessTokensWithEmptyJtis() {
        // Given
        UUID testUserId = UUID.randomUUID();
        Set<Object> emptySet = new HashSet<>();
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("user:tokens:" + testUserId.toString())).thenReturn(emptySet);

        // When
        redisTokenService.revokeAllUserAccessTokens(testUserId);

        // Then
        String userTokensKey = "user:tokens:" + testUserId.toString();
        verify(redisTemplate, never()).delete(eq(userTokensKey));
    }

    @Test
    void testIsAccessTokenValidByJti() {
        // Given
        String testJti = "test-jti-123";
        when(redisTemplate.hasKey("access:jti:" + testJti)).thenReturn(true);

        // When
        boolean result = redisTokenService.isAccessTokenValidByJti(testJti);

        // Then
        assertTrue(result);
        verify(redisTemplate).hasKey("access:jti:" + testJti);
    }

    @Test
    void testIsAccessTokenValidByJtiReturnsFalse() {
        // Given
        String testJti = "test-jti-123";
        when(redisTemplate.hasKey("access:jti:" + testJti)).thenReturn(false);

        // When
        boolean result = redisTokenService.isAccessTokenValidByJti(testJti);

        // Then
        assertFalse(result);
        verify(redisTemplate).hasKey("access:jti:" + testJti);
    }

    @Test
    void testIsRefreshTokenValidByJti() {
        // Given
        String testJti = "refresh-jti-456";
        when(redisTemplate.hasKey("refresh:jti:" + testJti)).thenReturn(true);

        // When
        boolean result = redisTokenService.isRefreshTokenValidByJti(testJti);

        // Then
        assertTrue(result);
        verify(redisTemplate).hasKey("refresh:jti:" + testJti);
    }

    @Test
    void testIsRefreshTokenValidByJtiReturnsFalse() {
        // Given
        String testJti = "refresh-jti-456";
        when(redisTemplate.hasKey("refresh:jti:" + testJti)).thenReturn(false);

        // When
        boolean result = redisTokenService.isRefreshTokenValidByJti(testJti);

        // Then
        assertFalse(result);
        verify(redisTemplate).hasKey("refresh:jti:" + testJti);
    }
}