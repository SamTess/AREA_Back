package area.server.AREA_Back.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT cookie configuration based on application properties.
 * Allows different configuration depending on the environment (dev/prod).
 */
@Component
@ConfigurationProperties(prefix = "app.jwt.cookie")
@Data
public class JwtCookieProperties {

    /**
     * Default access token expiry: 24 hours in seconds.
     */
    private static final int DEFAULT_ACCESS_TOKEN_EXPIRY = 86400;

    /**
     * Default refresh token expiry: 7 days in seconds.
     */
    private static final int DEFAULT_REFRESH_TOKEN_EXPIRY = 604800;

    /**
     * Whether cookies should be marked as Secure (HTTPS only).
     * true in production, false in development.
     */
    private boolean secure = false;

    /**
     * SameSite configuration for cookies.
     * Strict in production for maximum security.
     */
    private String sameSite = "None";

    /**
     * Domain for cookies (optional).
     * Useful for subdomains.
     */
    private String domain;

    /**
     * Access token lifetime in seconds.
     * Default is 24 hours (86400 seconds).
     */
    private int accessTokenExpiry = DEFAULT_ACCESS_TOKEN_EXPIRY;

    /**
     * Refresh token lifetime in seconds.
     * Default is 7 days (604800 seconds).
     */
    private int refreshTokenExpiry = DEFAULT_REFRESH_TOKEN_EXPIRY;
}