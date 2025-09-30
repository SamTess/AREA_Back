package area.server.AREA_Back.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class JwtService {

    private static final int MIN_KEY_LENGTH_BYTES = 32;
    private static final int BITS_PER_BYTE = 8;

    @Value("${JWT_ACCESS_SECRET:}")
    private String accessTokenSecret;

    @Value("${JWT_REFRESH_SECRET:}")
    private String refreshTokenSecret;

    @Value("${ACCESS_TOKEN_EXPIRES_IN:15m}")
    private String accessTokenExpiresIn;

    @Value("${REFRESH_TOKEN_EXPIRES_IN:7d}")
    private String refreshTokenExpiresIn;

    /**
     * Generate access token for user
     */
    public String generateAccessToken(UUID userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("type", "access");
        return generateToken(claims, userId.toString(), getAccessTokenExpiration(), getAccessTokenSigningKey());
    }

    /**
     * Generate refresh token for user
     */
    public String generateRefreshToken(UUID userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("type", "refresh");
        return generateToken(claims, userId.toString(), getRefreshTokenExpiration(), getRefreshTokenSigningKey());
    }

    /**
     * Extract user ID from access token
     */
    public UUID extractUserIdFromAccessToken(String token) {
        Claims claims = extractAllClaims(token, getAccessTokenSigningKey());
        String userId = claims.getSubject();
        return UUID.fromString(userId);
    }

    /**
     * Extract user ID from refresh token
     */
    public UUID extractUserIdFromRefreshToken(String token) {
        Claims claims = extractAllClaims(token, getRefreshTokenSigningKey());
        String userId = claims.getSubject();
        return UUID.fromString(userId);
    }

    /**
     * Extract email from access token
     */
    public String extractEmailFromAccessToken(String token) {
        Claims claims = extractAllClaims(token, getAccessTokenSigningKey());
        return claims.get("email", String.class);
    }

    /**
     * Extract email from refresh token
     */
    public String extractEmailFromRefreshToken(String token) {
        Claims claims = extractAllClaims(token, getRefreshTokenSigningKey());
        return claims.get("email", String.class);
    }

    /**
     * Validate access token
     */
    public boolean isAccessTokenValid(String token, UUID userId) {
        try {
            final UUID tokenUserId = extractUserIdFromAccessToken(token);
            return tokenUserId.equals(userId) && !isTokenExpired(token, getAccessTokenSigningKey());
        } catch (Exception e) {
            log.debug("Access token validation failed", e);
            return false;
        }
    }

    /**
     * Validate refresh token
     */
    public boolean isRefreshTokenValid(String token, UUID userId) {
        try {
            final UUID tokenUserId = extractUserIdFromRefreshToken(token);
            return tokenUserId.equals(userId) && !isTokenExpired(token, getRefreshTokenSigningKey());
        } catch (Exception e) {
            log.debug("Refresh token validation failed", e);
            return false;
        }
    }

    /**
     * Check if access token is expired
     */
    public boolean isAccessTokenExpired(String token) {
        return isTokenExpired(token, getAccessTokenSigningKey());
    }

    /**
     * Check if refresh token is expired
     */
    public boolean isRefreshTokenExpired(String token) {
        return isTokenExpired(token, getRefreshTokenSigningKey());
    }

    /**
     * Get access token expiration time in milliseconds
     */
    public long getAccessTokenExpirationMs() {
        return parseDuration(accessTokenExpiresIn).toMillis();
    }

    /**
     * Get refresh token expiration time in milliseconds
     */
    public long getRefreshTokenExpirationMs() {
        return parseDuration(refreshTokenExpiresIn).toMillis();
    }

    /**
     * Generate a secure base64-encoded secret key suitable for JWT HMAC-SHA algorithms
     * This method generates a 256-bit (32 bytes) key as required by the JWT specification
     */
    public static String generateSecureKey() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return java.util.Base64.getEncoder().encodeToString(key.getEncoded());
    }

    // Private helper methods

    private String generateToken(Map<String, Object> extraClaims, String subject,
                                  Date expiration, SecretKey signingKey) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    private Claims extractAllClaims(String token, SecretKey signingKey) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token, SecretKey signingKey) {
        try {
            Claims claims = extractAllClaims(token, signingKey);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true; // Consider expired if we can't parse the token
        }
    }

    private SecretKey getAccessTokenSigningKey() {
        if (accessTokenSecret == null || accessTokenSecret.trim().isEmpty()) {
            log.warn("No access token secret provided, generating a secure key");
            return Jwts.SIG.HS256.key().build();
        }

        try {
            byte[] keyBytes = Decoders.BASE64.decode(accessTokenSecret);
            // Check if the key is long enough for HS256
            if (keyBytes.length < MIN_KEY_LENGTH_BYTES) {
                log.warn("Access token secret is too short ({} bits), generating secure key",
                        keyBytes.length * BITS_PER_BYTE);
                return Jwts.SIG.HS256.key().build();
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            log.warn("Invalid access token secret format, generating secure key: {}", e.getMessage());
            return Jwts.SIG.HS256.key().build();
        }
    }

    private SecretKey getRefreshTokenSigningKey() {
        if (refreshTokenSecret == null || refreshTokenSecret.trim().isEmpty()) {
            log.warn("No refresh token secret provided, generating a secure key");
            return Jwts.SIG.HS256.key().build();
        }

        try {
            byte[] keyBytes = Decoders.BASE64.decode(refreshTokenSecret);
            // Check if the key is long enough for HS256
            if (keyBytes.length < MIN_KEY_LENGTH_BYTES) {
                log.warn("Refresh token secret is too short ({} bits), generating secure key",
                        keyBytes.length * BITS_PER_BYTE);
                return Jwts.SIG.HS256.key().build();
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            log.warn("Invalid refresh token secret format, generating secure key: {}", e.getMessage());
            return Jwts.SIG.HS256.key().build();
        }
    }

    private Date getAccessTokenExpiration() {
        Duration duration = parseDuration(accessTokenExpiresIn);
        return Date.from(Instant.now().plus(duration));
    }

    private Date getRefreshTokenExpiration() {
        Duration duration = parseDuration(refreshTokenExpiresIn);
        return Date.from(Instant.now().plus(duration));
    }

    /**
     * Parse duration string (e.g., "15m", "7d", "1h")
     */
    private Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            throw new IllegalArgumentException("Duration string cannot be null or empty");
        }

        String trimmed = durationStr.trim();
        if (trimmed.length() < 2) {
            throw new IllegalArgumentException("Invalid duration format: " + durationStr);
        }

        String unit = trimmed.substring(trimmed.length() - 1).toLowerCase();
        String numberPart = trimmed.substring(0, trimmed.length() - 1);

        try {
            long value = Long.parseLong(numberPart);

            return switch (unit) {
                case "s" -> Duration.ofSeconds(value);
                case "m" -> Duration.ofMinutes(value);
                case "h" -> Duration.ofHours(value);
                case "d" -> Duration.ofDays(value);
                default -> throw new IllegalArgumentException(
                    "Invalid duration unit: " + unit + ". Supported units: s, m, h, d");
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in duration: " + numberPart, e);
        }
    }
}