package area.server.AREA_Back.service.Redis;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.service.Auth.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisTokenService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtService jwtService;
    private final MeterRegistry meterRegistry;

    private Counter storeAccessTokenCalls;
    private Counter storeRefreshTokenCalls;
    private Counter accessTokenValidationCalls;
    private Counter refreshTokenValidationCalls;
    private Counter deleteAccessTokenCalls;
    private Counter deleteRefreshTokenCalls;
    private Counter tokenValidationFailures;

    @PostConstruct
    public void initMetrics() {
        storeAccessTokenCalls = Counter.builder("redis_token.store_access.calls")
                .description("Total number of access token storage calls")
                .register(meterRegistry);

        storeRefreshTokenCalls = Counter.builder("redis_token.store_refresh.calls")
                .description("Total number of refresh token storage calls")
                .register(meterRegistry);

        accessTokenValidationCalls = Counter.builder("redis_token.validate_access.calls")
                .description("Total number of access token validation calls")
                .register(meterRegistry);

        refreshTokenValidationCalls = Counter.builder("redis_token.validate_refresh.calls")
                .description("Total number of refresh token validation calls")
                .register(meterRegistry);

        deleteAccessTokenCalls = Counter.builder("redis_token.delete_access.calls")
                .description("Total number of access token deletion calls")
                .register(meterRegistry);

        deleteRefreshTokenCalls = Counter.builder("redis_token.delete_refresh.calls")
                .description("Total number of refresh token deletion calls")
                .register(meterRegistry);

        tokenValidationFailures = Counter.builder("redis_token.validation_failures")
                .description("Total number of token validation failures")
                .register(meterRegistry);
    }

    public void storeAccessToken(String accessToken, UUID userId) {
        String key = AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX + accessToken;
        Duration ttl = Duration.ofMillis(jwtService.getAccessTokenExpirationMs());

        redisTemplate.opsForValue().set(key, userId.toString(), ttl);
        storeAccessTokenCalls.increment();
    }

    public void storeRefreshToken(UUID userId, String refreshToken) {
        String key = AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX + userId.toString();
        Duration ttl = Duration.ofMillis(jwtService.getRefreshTokenExpirationMs());

        redisTemplate.opsForValue().set(key, refreshToken, ttl);
        storeRefreshTokenCalls.increment();
    }

    public boolean isAccessTokenValid(String accessToken) {
        accessTokenValidationCalls.increment();
        String key = AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX + accessToken;
        String userId = (String) redisTemplate.opsForValue().get(key);
        boolean isValid = userId != null;

        if (!isValid) {
            tokenValidationFailures.increment();
        }

        return isValid;
    }

    public UUID getUserIdFromAccessToken(String accessToken) {
        String key = AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX + accessToken;
        String userId = (String) redisTemplate.opsForValue().get(key);

        if (userId == null) {
            return null;
        }

        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in Redis for access token", e);
            return null;
        }
    }

    public String getRefreshToken(UUID userId) {
        String key = AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX + userId.toString();
        return (String) redisTemplate.opsForValue().get(key);
    }

    public boolean isRefreshTokenValid(UUID userId, String refreshToken) {
        String storedToken = getRefreshToken(userId);
        boolean isValid = storedToken != null && storedToken.equals(refreshToken);

        return isValid;
    }

    public void deleteAccessToken(String accessToken) {
        String key = AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX + accessToken;
        redisTemplate.delete(key);
        deleteAccessTokenCalls.increment();
    }

    public void deleteRefreshToken(UUID userId) {
        String key = AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX + userId.toString();
        redisTemplate.delete(key);
        deleteRefreshTokenCalls.increment();
    }

    public void deleteAllTokensForUser(UUID userId, String accessToken) {
        deleteAccessToken(accessToken);
        deleteRefreshToken(userId);
    }

    public void rotateRefreshToken(UUID userId, String newRefreshToken) {
        deleteRefreshToken(userId);
        storeRefreshToken(userId, newRefreshToken);
    }

    public void cleanupExpiredTokens() {
        // Redis TTL will automatically clean up expired tokens
    }

    public Long getAccessTokenTTL(String accessToken) {
        String key = AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX + accessToken;
        return redisTemplate.getExpire(key);
    }

    public Long getRefreshTokenTTL(UUID userId) {
        String key = AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX + userId.toString();
        return redisTemplate.getExpire(key);
    }

    public boolean hasActiveRefreshToken(UUID userId) {
        String key = AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX + userId.toString();
        return redisTemplate.hasKey(key);
    }

    public void extendAccessTokenTTL(String accessToken, Duration newTTL) {
        String key = AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX + accessToken;
        if (redisTemplate.hasKey(key)) {
            redisTemplate.expire(key, newTTL);
        }
    }
}