package area.server.AREA_Back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisTokenService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtService jwtService;

    private static final String ACCESS_TOKEN_PREFIX = "access:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    /**
     * Store access token in Redis with TTL
     */
    public void storeAccessToken(String accessToken, UUID userId) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        Duration ttl = Duration.ofMillis(jwtService.getAccessTokenExpirationMs());

        redisTemplate.opsForValue().set(key, userId.toString(), ttl);
        log.debug("Stored access token for user: {}", userId);
    }

    /**
     * Store refresh token in Redis for a user
     */
    public void storeRefreshToken(UUID userId, String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + userId.toString();
        Duration ttl = Duration.ofMillis(jwtService.getRefreshTokenExpirationMs());

        redisTemplate.opsForValue().set(key, refreshToken, ttl);
        log.debug("Stored refresh token for user: {}", userId);
    }

    /**
     * Validate access token exists in Redis
     */
    public boolean isAccessTokenValid(String accessToken) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        String userId = (String) redisTemplate.opsForValue().get(key);
        boolean isValid = userId != null;

        if (!isValid) {
            log.debug("Access token not found in Redis: {}", accessToken.substring(0, Math.min(10, accessToken.length())) + "...");
        }

        return isValid;
    }

    /**
     * Get user ID from access token in Redis
     */
    public UUID getUserIdFromAccessToken(String accessToken) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        String userId = (String) redisTemplate.opsForValue().get(key);

        if (userId == null) {
            log.debug("No user ID found for access token");
            return null;
        }

        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in Redis for access token", e);
            return null;
        }
    }

    /**
     * Get refresh token for a user
     */
    public String getRefreshToken(UUID userId) {
        String key = REFRESH_TOKEN_PREFIX + userId.toString();
        return (String) redisTemplate.opsForValue().get(key);
    }

    /**
     * Validate refresh token for a user
     */
    public boolean isRefreshTokenValid(UUID userId, String refreshToken) {
        String storedToken = getRefreshToken(userId);
        boolean isValid = storedToken != null && storedToken.equals(refreshToken);

        if (!isValid) {
            log.debug("Refresh token validation failed for user: {}", userId);
        }

        return isValid;
    }

    /**
     * Delete access token from Redis
     */
    public void deleteAccessToken(String accessToken) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Deleted access token: {}", deleted);
    }

    /**
     * Delete refresh token for a user
     */
    public void deleteRefreshToken(UUID userId) {
        String key = REFRESH_TOKEN_PREFIX + userId.toString();
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Deleted refresh token for user {}: {}", userId, deleted);
    }

    /**
     * Delete all tokens for a user (logout)
     */
    public void deleteAllTokensForUser(UUID userId, String accessToken) {
        deleteAccessToken(accessToken);
        deleteRefreshToken(userId);
        log.debug("Deleted all tokens for user: {}", userId);
    }

    /**
     * Rotate refresh token - delete old and store new
     */
    public void rotateRefreshToken(UUID userId, String newRefreshToken) {
        deleteRefreshToken(userId);
        storeRefreshToken(userId, newRefreshToken);
        log.debug("Rotated refresh token for user: {}", userId);
    }

    /**
     * Clean up expired tokens (this would typically be called by a scheduled task)
     */
    public void cleanupExpiredTokens() {
        // Redis automatically handles TTL expiration, so this is mainly for logging
        log.debug("Redis TTL will automatically clean up expired tokens");
    }

    /**
     * Get remaining TTL for access token in seconds
     */
    public Long getAccessTokenTTL(String accessToken) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        return redisTemplate.getExpire(key);
    }

    /**
     * Get remaining TTL for refresh token in seconds
     */
    public Long getRefreshTokenTTL(UUID userId) {
        String key = REFRESH_TOKEN_PREFIX + userId.toString();
        return redisTemplate.getExpire(key);
    }

    /**
     * Check if user has an active refresh token
     */
    public boolean hasActiveRefreshToken(UUID userId) {
        String key = REFRESH_TOKEN_PREFIX + userId.toString();
        return redisTemplate.hasKey(key);
    }

    /**
     * Extend access token TTL (if needed for "remember me" functionality)
     */
    public void extendAccessTokenTTL(String accessToken, Duration newTTL) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        if (redisTemplate.hasKey(key)) {
            redisTemplate.expire(key, newTTL);
            log.debug("Extended access token TTL to: {}", newTTL);
        }
    }
}