package area.server.AREA_Back.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private JwtService jwtService;

    private RedisTokenService redisTokenService;

    @BeforeEach
    void setUp() {
        redisTokenService = new RedisTokenService(redisTemplate, jwtService);
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
}