package area.server.AREA_Back.service;

import area.server.AREA_Back.constants.AuthTokenConstants;
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

    private static final int TOKEN_LOG_PREFIX_LENGTH = 10;

    public void storeAccessToken(String accessToken, UUID userId) {
        String key = AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX + accessToken;
        Duration ttl = Duration.ofMillis(jwtService.getAccessTokenExpirationMs());

        redisTemplate.opsForValue().set(key, userId.toString(), ttl);
        log.debug("Stored access token for user: { }", userId);
    }

    public void storeRefreshToken(UUID userId, String refreshToken) {
        String key = AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX + userId.toString();
        Duration ttl = Duration.ofMillis(jwtService.getRefreshTokenExpirationMs());

        redisTemplate.opsForValue().set(key, refreshToken, ttl);
        log.debug("Stored refresh token for user: { }", userId);
    }

    public boolean isAccessTokenValid(String accessToken) {
        String key = AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX + accessToken;
        String userId = (String) redisTemplate.opsForValue().get(key);
        boolean isValid = userId != null;

        if (!isValid) {
            String tokenPrefix = accessToken.substring(0,
                    Math.min(TOKEN_LOG_PREFIX_LENGTH, accessToken.length())) + "...";
            log.debug("Access token not found in Redis: { }", tokenPrefix);
        }

        return isValid;
    }

    public UUID getUserIdFromAccessToken(String accessToken) {
        String key = AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX + accessToken;
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

    public String getRefreshToken(UUID userId) {
        String key = AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX + userId.toString();
        return (String) redisTemplate.opsForValue().get(key);
    }

    public boolean isRefreshTokenValid(UUID userId, String refreshToken) {
        String storedToken = getRefreshToken(userId);
        boolean isValid = storedToken != null && storedToken.equals(refreshToken);

        if (!isValid) {
            log.debug("Refresh token validation failed for user: { }", userId);
        }

        return isValid;
    }

    public void deleteAccessToken(String accessToken) {
        String key = AuthTokenConstants.REDIS_ACCESS_TOKEN_PREFIX + accessToken;
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Deleted access token: { }", deleted);
    }

    public void deleteRefreshToken(UUID userId) {
        String key = AuthTokenConstants.REDIS_REFRESH_TOKEN_PREFIX + userId.toString();
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Deleted refresh token for user { }: { }", userId, deleted);
    }

    public void deleteAllTokensForUser(UUID userId, String accessToken) {
        deleteAccessToken(accessToken);
        deleteRefreshToken(userId);
        log.debug("Deleted all tokens for user: { }", userId);
    }

    public void rotateRefreshToken(UUID userId, String newRefreshToken) {
        deleteRefreshToken(userId);
        storeRefreshToken(userId, newRefreshToken);
        log.debug("Rotated refresh token for user: { }", userId);
    }

    public void cleanupExpiredTokens() {
        log.debug("Redis TTL will automatically clean up expired tokens");
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
            log.debug("Extended access token TTL to: { }", newTTL);
        }
    }
}