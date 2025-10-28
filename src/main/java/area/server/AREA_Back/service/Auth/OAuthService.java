package area.server.AREA_Back.service.Auth;

import area.server.AREA_Back.config.JwtCookieProperties;
import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Redis.RedisTokenService;
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
    protected final JwtCookieProperties jwtCookieProperties;

    protected PasswordEncoder passwordEncoder;
    protected UserOAuthIdentityRepository userOAuthIdentityRepository;
    protected UserRepository userRepository;
    protected RedisTokenService redisTokenService;

    @SuppressWarnings("ParameterNumber")
    protected OAuthService(
        String providerKey,
        String providerLabel,
        String providerLogoUrl,
        String userAuthUrl,
        String clientId,
        String clientSecret,
        JwtService jwtService,
        JwtCookieProperties jwtCookieProperties) {

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
        this.jwtCookieProperties = jwtCookieProperties;

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
    public String getUserAuthUrl(String state) {
        if (state != null && !state.isEmpty()) {
            String separator = userAuthUrl.contains("?") ? "&" : "?";
            return userAuthUrl + separator + "state=" + state;
        }
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
        String secureFlag = "";
        if (jwtCookieProperties.isSecure()) {
            secureFlag = "Secure; ";
        }
        
        String domainAttribute = "";
        if (jwtCookieProperties.getDomain() != null && !jwtCookieProperties.getDomain().isEmpty()) {
            domainAttribute = "Domain=" + jwtCookieProperties.getDomain() + "; ";
        }

        response.setHeader("Set-Cookie", String.format(
            "%s=%s; Path=/; Max-Age=%d; HttpOnly; %s%sSameSite=%s",
            AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME,
            accessToken,
            jwtCookieProperties.getAccessTokenExpiry(),
            domainAttribute,
            secureFlag,
            jwtCookieProperties.getSameSite()
        ));

        response.addHeader("Set-Cookie", String.format(
            "%s=%s; Path=/; Max-Age=%d; HttpOnly; %s%sSameSite=%s",
            AuthTokenConstants.REFRESH_TOKEN_COOKIE_NAME,
            refreshToken,
            jwtCookieProperties.getRefreshTokenExpiry(),
            domainAttribute,
            secureFlag,
            jwtCookieProperties.getSameSite()
        ));
    }
}
