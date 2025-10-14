package area.server.AREA_Back.service.Auth;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;


public abstract class OAuthService {
    protected final String providerKey;
    protected final String providerLabel;
    protected final String providerLogoUrl;
    protected final String userAuthUrl;
    protected final String clientId;
    protected final String clientSecret;
    protected final JwtService jwtService;

    protected PasswordEncoder passwordEncoder;
    protected UserOAuthIdentityRepository userOAuthIdentityRepository;
    protected UserRepository userRepository;
    protected RedisTokenService redisTokenService;

    private static final int ACCESS_TOKEN_COOKIE_MAX_AGE = 15 * 60;
    private static final int REFRESH_TOKEN_COOKIE_MAX_AGE = 7 * 24 * 60 * 60;

    protected OAuthService(
        String providerKey,
        String providerLabel,
        String providerLogoUrl,
        String userAuthUrl,
        String clientId,
        String clientSecret,
        JwtService jwtService) {

        if (clientId == null || clientId.isEmpty()
            || clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalStateException(providerKey
                + " OAuth2 client ID and secret must be set in environment variables.");
        }

        this.providerKey = providerKey;
        this.providerLabel = providerLabel;
        this.providerLogoUrl = providerLogoUrl;
        this.userAuthUrl = userAuthUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.jwtService = jwtService;

    }

    public String getProviderKey() {
        return providerKey;
    }
    public String getProviderLabel() {
        return providerLabel;
    }
    public String getProviderLogoUrl() {
        return providerLogoUrl;
    }
    public String getUserAuthUrl() {
        return userAuthUrl;
    }
    public String getClientId() {
        return clientId;
    }
    public String getClientSecret() {
        return clientSecret;
    }

    public abstract AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response);

    protected void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        Cookie accessCookie = new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, accessToken);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(false);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(ACCESS_TOKEN_COOKIE_MAX_AGE);
        response.addCookie(accessCookie);

        Cookie refreshCookie = new Cookie(AuthTokenConstants.REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(REFRESH_TOKEN_COOKIE_MAX_AGE);
        response.addCookie(refreshCookie);
    }
}
