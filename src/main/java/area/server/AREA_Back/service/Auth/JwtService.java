package area.server.AREA_Back.service.Auth;

import area.server.AREA_Back.config.JwtCookieProperties;
import area.server.AREA_Back.constants.AuthTokenConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@Slf4j
public class JwtService {

    private static final int MIN_KEY_LENGTH_BYTES = 32;
    private static final int BITS_PER_BYTE = 8;
    private static final int LOG_TOKEN_PREFIX_LENGTH = 20;
    private static final long MILLISECONDS_PER_SECOND = 1000L;

    @Value("${app.jwt.access-secret:}")
    private String accessTokenSecret;

    @Value("${app.jwt.refresh-secret:}")
    private String refreshTokenSecret;

    private final JwtCookieProperties jwtCookieProperties;
    private final MeterRegistry meterRegistry;

    private Counter generateAccessTokenCalls;
    private Counter generateRefreshTokenCalls;
    private Counter accessTokenValidationCalls;
    private Counter refreshTokenValidationCalls;
    private Counter tokenValidationFailures;

    public JwtService(JwtCookieProperties jwtCookieProperties, MeterRegistry meterRegistry) {
        this.jwtCookieProperties = jwtCookieProperties;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initMetrics() {
        generateAccessTokenCalls = Counter.builder("jwt.generate_access.calls")
                .description("Total number of access token generation calls")
                .register(meterRegistry);

        generateRefreshTokenCalls = Counter.builder("jwt.generate_refresh.calls")
                .description("Total number of refresh token generation calls")
                .register(meterRegistry);

        accessTokenValidationCalls = Counter.builder("jwt.validate_access.calls")
                .description("Total number of access token validation calls")
                .register(meterRegistry);

        refreshTokenValidationCalls = Counter.builder("jwt.validate_refresh.calls")
                .description("Total number of refresh token validation calls")
                .register(meterRegistry);

        tokenValidationFailures = Counter.builder("jwt.validation_failures")
                .description("Total number of JWT validation failures")
                .register(meterRegistry);
        validateJwtSecrets();
    }

    /**
     * Validate that JWT secrets are properly configured.
     * Fails fast if secrets are missing or too short.
     */
    private void validateJwtSecrets() {
        if (accessTokenSecret == null || accessTokenSecret.trim().isEmpty()) {
            log.error("JWT_ACCESS_SECRET is not set! Application cannot start securely.");
            throw new IllegalStateException(
                "JWT_ACCESS_SECRET is mandatory. Please set it in environment variables. " +
                "Generate with: openssl rand -base64 32"
            );
        }
        if (refreshTokenSecret == null || refreshTokenSecret.trim().isEmpty()) {
            log.error("JWT_REFRESH_SECRET is not set! Application cannot start securely.");
            throw new IllegalStateException(
                "JWT_REFRESH_SECRET is mandatory. Please set it in environment variables. " +
                "Generate with: openssl rand -base64 32"
            );
        }
        try {
            byte[] accessKeyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(accessTokenSecret);
            if (accessKeyBytes.length < MIN_KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                    String.format("JWT_ACCESS_SECRET must be at least 256 bits (32 bytes). " +
                    "Current: %d bits. Generate with: openssl rand -base64 32",
                    accessKeyBytes.length * BITS_PER_BYTE)
                );
            }
            byte[] refreshKeyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(refreshTokenSecret);
            if (refreshKeyBytes.length < MIN_KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                    String.format("JWT_REFRESH_SECRET must be at least 256 bits (32 bytes). " +
                    "Current: %d bits. Generate with: openssl rand -base64 32",
                    refreshKeyBytes.length * BITS_PER_BYTE)
                );
            }
            log.info("JWT secrets validated successfully (access: {} bits, refresh: {} bits)",
                accessKeyBytes.length * BITS_PER_BYTE,
                refreshKeyBytes.length * BITS_PER_BYTE);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("JWT secrets are not valid Base64 encoded strings");
            throw new IllegalStateException(
                "JWT secrets must be Base64 encoded. Generate with: openssl rand -base64 32", e
            );
        }
    }

    public String generateAccessToken(UUID userId, String email) {
        generateAccessTokenCalls.increment();
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("type", AuthTokenConstants.ACCESS_TOKEN_TYPE);
        claims.put("jti", UUID.randomUUID().toString());
        return generateToken(claims, userId.toString(), getAccessTokenExpiration(), getAccessTokenSigningKey());
    }
    public String generateRefreshToken(UUID userId, String email) {
        generateRefreshTokenCalls.increment();
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("type", AuthTokenConstants.REFRESH_TOKEN_TYPE);
        claims.put("jti", UUID.randomUUID().toString());
        return generateToken(claims, userId.toString(), getRefreshTokenExpiration(), getRefreshTokenSigningKey());
    }

    public UUID extractUserIdFromAccessToken(String token) {
        try {
            log.debug("Extracting user ID from access token (prefix: { }...)",
                token.substring(0, Math.min(LOG_TOKEN_PREFIX_LENGTH, token.length())));
            Claims claims = extractAllClaims(token, getAccessTokenSigningKey());
            String userId = claims.getSubject();
            log.debug("Extracted user ID: { }", userId);
            return UUID.fromString(userId);
        } catch (Exception e) {
            log.error("Failed to extract user ID from access token: { }", e.getMessage(), e);
            throw e;
        }
    }

    public UUID extractUserIdFromRefreshToken(String token) {
        Claims claims = extractAllClaims(token, getRefreshTokenSigningKey());
        String userId = claims.getSubject();
        return UUID.fromString(userId);
    }

    public String extractEmailFromAccessToken(String token) {
        Claims claims = extractAllClaims(token, getAccessTokenSigningKey());
        return claims.get("email", String.class);
    }

    public String extractEmailFromRefreshToken(String token) {
        Claims claims = extractAllClaims(token, getRefreshTokenSigningKey());
        return claims.get("email", String.class);
    }

    public String extractJtiFromAccessToken(String token) {
        Claims claims = extractAllClaims(token, getAccessTokenSigningKey());
        return claims.get("jti", String.class);
    }

    public String extractJtiFromRefreshToken(String token) {
        Claims claims = extractAllClaims(token, getRefreshTokenSigningKey());
        return claims.get("jti", String.class);
    }

    public boolean isAccessTokenValid(String token, UUID userId) {
        accessTokenValidationCalls.increment();
        try {
            final UUID tokenUserId = extractUserIdFromAccessToken(token);
            return tokenUserId.equals(userId) && !isTokenExpired(token, getAccessTokenSigningKey());
        } catch (Exception e) {
            tokenValidationFailures.increment();
            log.debug("Access token validation failed", e);
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token, UUID userId) {
        refreshTokenValidationCalls.increment();
        try {
            final UUID tokenUserId = extractUserIdFromRefreshToken(token);
            return tokenUserId.equals(userId) && !isTokenExpired(token, getRefreshTokenSigningKey());
        } catch (Exception e) {
            tokenValidationFailures.increment();
            log.debug("Refresh token validation failed", e);
            return false;
        }
    }

    public boolean isAccessTokenExpired(String token) {
        return isTokenExpired(token, getAccessTokenSigningKey());
    }

    public boolean isRefreshTokenExpired(String token) {
        return isTokenExpired(token, getRefreshTokenSigningKey());
    }

    public long getAccessTokenExpirationMs() {
        return jwtCookieProperties.getAccessTokenExpiry() * MILLISECONDS_PER_SECOND;
    }

    public long getRefreshTokenExpirationMs() {
        return jwtCookieProperties.getRefreshTokenExpiry() * MILLISECONDS_PER_SECOND;
    }

    public static String generateSecureKey() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        return java.util.Base64.getEncoder().encodeToString(key.getEncoded());
    }

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
        try {
            log.debug("Parsing JWT token with signing key");
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Failed to parse JWT token: { }", e.getMessage(), e);
            throw e;
        }
    }

    private boolean isTokenExpired(String token, SecretKey signingKey) {
        try {
            Claims claims = extractAllClaims(token, signingKey);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    private SecretKey getAccessTokenSigningKey() {
        log.debug("Getting access token signing key");

        try {
            byte[] keyBytes = Decoders.BASE64.decode(accessTokenSecret);
            log.debug("Using configured access token secret ({} bytes)", keyBytes.length);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            log.error("Failed to decode access token secret: {}", e.getMessage());
            throw new IllegalStateException("Invalid JWT_ACCESS_SECRET configuration", e);
        }
    }

    private SecretKey getRefreshTokenSigningKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(refreshTokenSecret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            log.error("Failed to decode refresh token secret: {}", e.getMessage());
            throw new IllegalStateException("Invalid JWT_REFRESH_SECRET configuration", e);
        }
    }

    private Date getAccessTokenExpiration() {
        long expirySeconds = jwtCookieProperties.getAccessTokenExpiry();
        return Date.from(Instant.now().plusSeconds(expirySeconds));
    }

    private Date getRefreshTokenExpiration() {
        long expirySeconds = jwtCookieProperties.getRefreshTokenExpiry();
        return Date.from(Instant.now().plusSeconds(expirySeconds));
    }
}
