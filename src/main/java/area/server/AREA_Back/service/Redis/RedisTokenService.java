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

        try {
            String jti = jwtService.extractJtiFromAccessToken(accessToken);
            if (jti != null) {
                String jtiKey = AuthTokenConstants.REDIS_ACCESS_JTI_PREFIX + jti;
                redisTemplate.opsForValue().set(jtiKey, userId.toString(), ttl);

                String userTokensKey = AuthTokenConstants.REDIS_USER_TOKENS_PREFIX + userId.toString();
                redisTemplate.opsForSet().add(userTokensKey, jti);
                redisTemplate.expire(userTokensKey, ttl);
            }
        } catch (Exception e) {
            log.warn("Failed to extract/store JTI for access token", e);
        }

        storeAccessTokenCalls.increment();
    }

    public void storeRefreshToken(UUID userId, String refreshToken) {
        String key = AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX + userId.toString();
        Duration ttl = Duration.ofMillis(jwtService.getRefreshTokenExpirationMs());

        redisTemplate.opsForValue().set(key, refreshToken, ttl);

        try {
            String jti = jwtService.extractJtiFromRefreshToken(refreshToken);
            if (jti != null) {
                String jtiKey = AuthTokenConstants.REDIS_REFRESH_JTI_PREFIX + jti;
                redisTemplate.opsForValue().set(jtiKey, userId.toString(), ttl);
            }
        } catch (Exception e) {
            log.warn("Failed to extract/store JTI for refresh token", e);
        }

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

        if (!isValid && refreshToken != null) {
            String usedKey = AuthTokenConstants.REDIS_USED_REFRESH_PREFIX + refreshToken;
            Boolean wasUsed = redisTemplate.hasKey(usedKey);
            if (Boolean.TRUE.equals(wasUsed)) {
                log.error("SECURITY ALERT: Refresh token reuse detected for user {}. " +
                    "This indicates a possible token theft. Revoking all tokens.", userId);
                try {
                    deleteAllTokensForUser(userId, null);
                } catch (Exception e) {
                    log.error("Failed to revoke tokens for user {} after replay detection", userId, e);
                }
                return false;
            }
        }

        return isValid;
    }

    public void deleteAccessToken(String accessToken) {
        String key = AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX + accessToken;

        try {
            String jti = jwtService.extractJtiFromAccessToken(accessToken);
            if (jti != null) {
                UUID userId = getUserIdFromAccessToken(accessToken);

                String jtiKey = AuthTokenConstants.REDIS_ACCESS_JTI_PREFIX + jti;
                redisTemplate.delete(jtiKey);

                if (userId != null) {
                    String userTokensKey = AuthTokenConstants.REDIS_USER_TOKENS_PREFIX + userId.toString();
                    redisTemplate.opsForSet().remove(userTokensKey, jti);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to clean up JTI data for access token", e);
        }

        redisTemplate.delete(key);
        deleteAccessTokenCalls.increment();
    }

    public void deleteRefreshToken(UUID userId) {
        String key = AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX + userId.toString();

        try {
            String refreshToken = getRefreshToken(userId);
            if (refreshToken != null) {
                String jti = jwtService.extractJtiFromRefreshToken(refreshToken);
                if (jti != null) {
                    String jtiKey = AuthTokenConstants.REDIS_REFRESH_JTI_PREFIX + jti;
                    redisTemplate.delete(jtiKey);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to clean up JTI data for refresh token", e);
        }

        redisTemplate.delete(key);
        deleteRefreshTokenCalls.increment();
    }

    public void deleteAllTokensForUser(UUID userId, String accessToken) {
        deleteAccessToken(accessToken);
        deleteRefreshToken(userId);
    }

    public void rotateRefreshToken(UUID userId, String newRefreshToken) {
        String oldToken = getRefreshToken(userId);

        if (oldToken != null && !oldToken.isEmpty()) {
            String usedKey = AuthTokenConstants.REDIS_USED_REFRESH_PREFIX + oldToken;
            redisTemplate.opsForValue().set(usedKey, "used", Duration.ofDays(7));
            log.debug("Marked old refresh token as used for user: {}", userId);
        }
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

    public void revokeAccessTokenByJti(String jti) {
        String jtiKey = AuthTokenConstants.REDIS_ACCESS_JTI_PREFIX + jti;
        String userIdStr = (String) redisTemplate.opsForValue().get(jtiKey);

        if (userIdStr != null) {
            try {
                UUID userId = UUID.fromString(userIdStr);
                String userTokensKey = AuthTokenConstants.REDIS_USER_TOKENS_PREFIX + userId.toString();
                redisTemplate.opsForSet().remove(userTokensKey, jti);
            } catch (IllegalArgumentException e) {
                log.error("Invalid UUID format in JTI mapping: {}", userIdStr, e);
            }
        }

        redisTemplate.delete(jtiKey);
        log.info("Revoked access token with JTI: {}", jti);
    }

    public void revokeRefreshTokenByJti(String jti) {
        String jtiKey = AuthTokenConstants.REDIS_REFRESH_JTI_PREFIX + jti;
        redisTemplate.delete(jtiKey);
        log.info("Revoked refresh token with JTI: {}", jti);
    }

    public java.util.Set<Object> getUserActiveTokenJtis(UUID userId) {
        String userTokensKey = AuthTokenConstants.REDIS_USER_TOKENS_PREFIX + userId.toString();
        return redisTemplate.opsForSet().members(userTokensKey);
    }

    public void revokeAllUserAccessTokens(UUID userId) {
        String userTokensKey = AuthTokenConstants.REDIS_USER_TOKENS_PREFIX + userId.toString();
        java.util.Set<Object> jtis = redisTemplate.opsForSet().members(userTokensKey);

        if (jtis != null && !jtis.isEmpty()) {
            for (Object jti : jtis) {
                String jtiKey = AuthTokenConstants.REDIS_ACCESS_JTI_PREFIX + jti.toString();
                redisTemplate.delete(jtiKey);
            }
            redisTemplate.delete(userTokensKey);
            log.info("Revoked {} access tokens for user: {}", jtis.size(), userId);
        }
    }

    public boolean isAccessTokenValidByJti(String jti) {
        String jtiKey = AuthTokenConstants.REDIS_ACCESS_JTI_PREFIX + jti;
        return redisTemplate.hasKey(jtiKey);
    }

    public boolean isRefreshTokenValidByJti(String jti) {
        String jtiKey = AuthTokenConstants.REDIS_REFRESH_JTI_PREFIX + jti;
        return redisTemplate.hasKey(jtiKey);
    }
}