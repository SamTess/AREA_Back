package area.server.AREA_Back.constants;

/**
 * Centralized constants for authentication token names
 * Used throughout the application to ensure consistency
 */
public final class AuthTokenConstants {

    private AuthTokenConstants() {
        // Prevents instantiation of this constants class
    }

    /**
     * HTTP-Only cookie name for the access token
     */
    public static final String ACCESS_TOKEN_COOKIE_NAME = "authToken";

    /**
     * HTTP-Only cookie name for the refresh token
     */
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    /**
     * Redis prefix for access tokens
     */
    public static final String REDIS_ACCESS_TOKEN_PREFIX = "access:";

    /**
     * Redis prefix for refresh tokens
     */
    public static final String REDIS_REFRESH_TOKEN_PREFIX = "refresh:";

    /**
     * Redis prefix for access token JTI mapping
     */
    public static final String REDIS_ACCESS_JTI_PREFIX = "access:jti:";

    /**
     * Redis prefix for refresh token JTI mapping
     */
    public static final String REDIS_REFRESH_JTI_PREFIX = "refresh:jti:";

    /**
     * Redis prefix for user active tokens set
     */
    public static final String REDIS_USER_TOKENS_PREFIX = "user:tokens:";

    /**
     * Redis prefix for used refresh tokens (replay detection)
     */
    public static final String REDIS_USED_REFRESH_PREFIX = "used:refresh:";

    /**
     * TTL for used refresh tokens (7 days)
     */
    public static final java.time.Duration USED_REFRESH_TOKEN_TTL = java.time.Duration.ofDays(7);

    /**
     * Token type in the JWT payload for access tokens
     */
    public static final String ACCESS_TOKEN_TYPE = "access";

    /**
     * Token type in the JWT payload for refresh tokens
     */
    public static final String REFRESH_TOKEN_TYPE = "refresh";
}