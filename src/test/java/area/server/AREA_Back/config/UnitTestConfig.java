package area.server.AREA_Back.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("unit-test")
public class UnitTestConfig {
    // Configuration spécifique aux tests unitaires
    // Les beans Redis et Database sont automatiquement mockés

    /**
     * Test access token expiry: 24 hours in seconds.
     */
    private static final int TEST_ACCESS_TOKEN_EXPIRY = 86400;

    /**
     * Test refresh token expiry: 7 days in seconds.
     */
    private static final int TEST_REFRESH_TOKEN_EXPIRY = 604800;

    @Bean
    public JwtCookieProperties jwtCookieProperties() {
        JwtCookieProperties properties = new JwtCookieProperties();
        properties.setSecure(false);
        properties.setSameSite("Strict");
        properties.setAccessTokenExpiry(TEST_ACCESS_TOKEN_EXPIRY);
        properties.setRefreshTokenExpiry(TEST_REFRESH_TOKEN_EXPIRY);
        return properties;
    }
}