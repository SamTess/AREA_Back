package area.server.AREA_Back.service.Auth;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import area.server.AREA_Back.config.JwtCookieProperties;
import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth service for Spotify authentication
 * Handles user authentication and account linking via Spotify
 */
@Slf4j
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.spotify.client-id")
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.spotify.client-secret")
@Service
public class OAuthSpotifyService extends OAuthService {

    private static final int DEFAULT_TOKEN_EXPIRY_DAYS = 7;
    private static final String SPOTIFY_SCOPES = "user-read-email user-read-private user-library-read user-library-modify playlist-read-private playlist-modify-public playlist-modify-private user-read-playback-state user-modify-playback-state user-read-currently-playing";

    private final RestTemplate restTemplate;
    private final String redirectBaseUrl;
    private final TokenEncryptionService tokenEncryptionService;
    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;
    private final AuthService authService;

    private Counter oauthLoginSuccessCounter;
    private Counter oauthLoginFailureCounter;
    private Counter authenticateCalls;
    private Counter tokenExchangeCalls;
    private Counter tokenExchangeFailures;

    public OAuthSpotifyService(
        @Value("${spring.security.oauth2.client.registration.spotify.client-id}") final String spotifyClientId,
        @Value("${spring.security.oauth2.client.registration.spotify.client-secret}")
        final String spotifyClientSecret,
        @Value("${OAUTH_REDIRECT_BASE_URL:http://localhost:3000}") final String redirectBaseUrl,
        final JwtService jwtService,
        final JwtCookieProperties jwtCookieProperties,
        final MeterRegistry meterRegistry,
        final RedisTokenService redisTokenService,
        final PasswordEncoder passwordEncoder,
        final TokenEncryptionService tokenEncryptionService,
        final UserOAuthIdentityRepository userOAuthIdentityRepository,
        final UserRepository userRepository,
        final AuthService authService,
        final RestTemplate restTemplate
    ) {
        super("spotify",
            "Spotify",
            "https://img.icons8.com/color/48/spotify.png",
            "https://accounts.spotify.com/authorize?"
                + "client_id=" + spotifyClientId
                + "&response_type=code"
                + "&redirect_uri=" + redirectBaseUrl + "/api/oauth-callback"
                + "&scope=" + SPOTIFY_SCOPES,
            spotifyClientId,
            spotifyClientSecret,
            jwtService,
            jwtCookieProperties);
        this.redirectBaseUrl = redirectBaseUrl;
        this.meterRegistry = meterRegistry;
        this.redisTokenService = redisTokenService;
        this.tokenEncryptionService = tokenEncryptionService;
        this.userOAuthIdentityRepository = userOAuthIdentityRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    private void initMetrics() {
        this.oauthLoginSuccessCounter = Counter.builder("oauth.spotify.login.success")
            .description("Number of successful Spotify OAuth logins")
            .register(meterRegistry);

        this.oauthLoginFailureCounter = Counter.builder("oauth.spotify.login.failure")
            .description("Number of failed Spotify OAuth logins")
            .register(meterRegistry);

        this.authenticateCalls = Counter.builder("oauth.spotify.authenticate.calls")
            .description("Total number of Spotify OAuth authenticate calls")
            .register(meterRegistry);

        this.tokenExchangeCalls = Counter.builder("oauth.spotify.token_exchange.calls")
            .description("Total number of Spotify token exchange calls")
            .register(meterRegistry);

        this.tokenExchangeFailures = Counter.builder("oauth.spotify.token_exchange.failures")
            .description("Total number of Spotify token exchange failures")
            .register(meterRegistry);
    }

    @Override
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        authenticateCalls.increment();
        try {
            log.info("Starting Spotify OAuth authentication");
            SpotifyTokenResponse tokenResponse = exchangeCodeForToken(request.getAuthorizationCode());
            log.info("Successfully exchanged code for Spotify access token");

            UserProfileData profileData = fetchUserProfile(tokenResponse.accessToken);
            log.info("Fetched Spotify user profile: {}", profileData.userIdentifier);

            if (profileData.email == null || profileData.email.isEmpty()) {
                oauthLoginFailureCounter.increment();
                throw new RuntimeException("Spotify account email is required for authentication");
            }

            log.info("Calling authService.oauthLogin for email: {}", profileData.email);
            AuthResponse authResponse = authService.oauthLogin(profileData.email, profileData.avatarUrl, response);
            log.info("AuthService.oauthLogin completed successfully for user: {}", authResponse.getUser().getId());

            User user = userRepository.findById(authResponse.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found after login"));

            UserOAuthIdentity oauth = handleUserAuthentication(
                user,
                profileData,
                tokenResponse.accessToken,
                tokenResponse.refreshToken,
                tokenResponse.expiresIn
            );

            userOAuthIdentityRepository.save(oauth);
            log.info("Saved OAuth identity successfully");

            oauthLoginSuccessCounter.increment();
            log.info("Spotify OAuth authentication completed successfully for user: {}", user.getId());
            return authResponse;
        } catch (Exception e) {
            log.error("Spotify OAuth authentication failed", e);
            oauthLoginFailureCounter.increment();
            throw new RuntimeException("OAuth authentication failed: " + e.getMessage(), e);
        }
    }

    public UserOAuthIdentity linkToExistingUser(User existingUser, String authorizationCode) {
        SpotifyTokenResponse tokenResponse = exchangeCodeForToken(authorizationCode);
        UserProfileData profileData = fetchUserProfile(tokenResponse.accessToken);

        if (profileData.email == null || profileData.email.isEmpty()) {
            throw new RuntimeException("Spotify account email is required for account linking");
        }

        Optional<UserOAuthIdentity> existingOAuth = userOAuthIdentityRepository
            .findByProviderAndProviderUserId(this.providerKey, profileData.userIdentifier);

        if (existingOAuth.isPresent() && !existingOAuth.get().getUser().getId().equals(existingUser.getId())) {
            throw new RuntimeException("This Spotify account is already linked to another user");
        }

        Optional<UserOAuthIdentity> userOAuthOpt = userOAuthIdentityRepository
            .findByUserAndProvider(existingUser, this.providerKey);

        UserOAuthIdentity oauth;
        if (userOAuthOpt.isPresent()) {
            oauth = userOAuthOpt.get();
            oauth.setProviderUserId(profileData.userIdentifier);
            oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(tokenResponse.accessToken));
            if (tokenResponse.refreshToken != null && !tokenResponse.refreshToken.isEmpty()) {
                oauth.setRefreshTokenEnc(tokenEncryptionService.encryptToken(tokenResponse.refreshToken));
            }
            oauth.setExpiresAt(calculateExpirationTime(tokenResponse.expiresIn));
            oauth.setUpdatedAt(LocalDateTime.now());
        } else {
            oauth = new UserOAuthIdentity();
            oauth.setUser(existingUser);
            oauth.setProvider(this.providerKey);
            oauth.setProviderUserId(profileData.userIdentifier);
            oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(tokenResponse.accessToken));
            if (tokenResponse.refreshToken != null && !tokenResponse.refreshToken.isEmpty()) {
                oauth.setRefreshTokenEnc(tokenEncryptionService.encryptToken(tokenResponse.refreshToken));
            }
            oauth.setExpiresAt(calculateExpirationTime(tokenResponse.expiresIn));
            oauth.setCreatedAt(LocalDateTime.now());
            oauth.setUpdatedAt(LocalDateTime.now());
        }

        Map<String, Object> tokenMeta = new HashMap<>();
        tokenMeta.put("email", profileData.email);
        tokenMeta.put("display_name", profileData.displayName);
        tokenMeta.put("avatar_url", profileData.avatarUrl);
        tokenMeta.put("country", profileData.country);
        tokenMeta.put("product", profileData.product);
        oauth.setTokenMeta(tokenMeta);

        try {
            oauth = userOAuthIdentityRepository.save(oauth);
            log.info("Successfully linked Spotify account {} to user {}",
                    profileData.userIdentifier, existingUser.getId());
            return oauth;
        } catch (Exception e) {
            log.error("Failed to link Spotify account to existing user", e);
            throw new RuntimeException("Failed to link Spotify account: " + e.getMessage(), e);
        }
    }

    private UserOAuthIdentity handleUserAuthentication(
            User user,
            UserProfileData profileData,
            String accessToken,
            String refreshToken,
            Integer expiresIn) {

        Optional<UserOAuthIdentity> oauthOpt = userOAuthIdentityRepository.findByUserAndProvider(user,
            this.providerKey);

        UserOAuthIdentity oauth;
        if (oauthOpt.isPresent()) {
            oauth = oauthOpt.get();
            oauth.setProviderUserId(profileData.userIdentifier);
            oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(accessToken));
            if (refreshToken != null && !refreshToken.isEmpty()) {
                oauth.setRefreshTokenEnc(tokenEncryptionService.encryptToken(refreshToken));
            }
            oauth.setExpiresAt(calculateExpirationTime(expiresIn));
            oauth.setUpdatedAt(LocalDateTime.now());
            log.info("Updated existing OAuth identity for user: {}", user.getId());
        } else {
            oauth = new UserOAuthIdentity();
            oauth.setUser(user);
            oauth.setProvider(this.providerKey);
            oauth.setProviderUserId(profileData.userIdentifier);
            oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(accessToken));
            if (refreshToken != null && !refreshToken.isEmpty()) {
                oauth.setRefreshTokenEnc(tokenEncryptionService.encryptToken(refreshToken));
            }
            oauth.setExpiresAt(calculateExpirationTime(expiresIn));
            oauth.setCreatedAt(LocalDateTime.now());
            oauth.setUpdatedAt(LocalDateTime.now());
            log.info("Created new OAuth identity for user: {}", user.getId());
        }

        Map<String, Object> tokenMeta = new HashMap<>();
        tokenMeta.put("email", profileData.email);
        tokenMeta.put("display_name", profileData.displayName);
        tokenMeta.put("avatar_url", profileData.avatarUrl);
        tokenMeta.put("country", profileData.country);
        tokenMeta.put("product", profileData.product);
        oauth.setTokenMeta(tokenMeta);

        return oauth;
    }

    private LocalDateTime calculateExpirationTime(Integer expiresIn) {
        if (expiresIn == null || expiresIn <= 0) {
            return LocalDateTime.now().plusDays(DEFAULT_TOKEN_EXPIRY_DAYS);
        }
        return LocalDateTime.now().plusSeconds(expiresIn);
    }

    private SpotifyTokenResponse exchangeCodeForToken(String authorizationCode) {
        tokenExchangeCalls.increment();
        try {
            String tokenUrl = "https://accounts.spotify.com/api/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(this.clientId, this.clientSecret);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("code", authorizationCode);
            body.add("redirect_uri", this.redirectBaseUrl + "/api/oauth-callback");

            HttpEntity<MultiValueMap<String, String>> spotifyRequest = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> tokenResponse = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                spotifyRequest,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("Failed to exchange token with Spotify");
            }

            Map<String, Object> body2 = tokenResponse.getBody();
            Object accessTokenObj = body2.get("access_token");
            if (accessTokenObj == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("No access_token in Spotify response");
            }

            Object refreshTokenObj = body2.get("refresh_token");
            String refreshToken = null;
            if (refreshTokenObj != null) {
                refreshToken = refreshTokenObj.toString();
            }

            Object expiresInObj = body2.get("expires_in");
            Integer expiresIn = null;
            if (expiresInObj instanceof Integer) {
                expiresIn = (Integer) expiresInObj;
            } else if (expiresInObj != null) {
                try {
                    expiresIn = Integer.parseInt(expiresInObj.toString());
                } catch (NumberFormatException e) {
                    log.warn("Could not parse expires_in: {}", expiresInObj);
                }
            }

            return new SpotifyTokenResponse(accessTokenObj.toString(), refreshToken, expiresIn);
        } catch (Exception e) {
            tokenExchangeFailures.increment();
            log.error("Token exchange failed", e);
            throw new RuntimeException("Failed to exchange authorization code: " + e.getMessage(), e);
        }
    }

    private UserProfileData fetchUserProfile(String spotifyAccessToken) {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(spotifyAccessToken);
        authHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> authRequest = new HttpEntity<>(authHeaders);

        ResponseEntity<Map<String, Object>> userResp = restTemplate.exchange(
            "https://api.spotify.com/v1/me",
            HttpMethod.GET,
            authRequest,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        if (!userResp.getStatusCode().is2xxSuccessful() || userResp.getBody() == null) {
            throw new RuntimeException("Failed to fetch Spotify user profile");
        }

        Map<String, Object> userBody = userResp.getBody();

        Object idObj = userBody.get("id");
        if (idObj == null) {
            throw new RuntimeException("Spotify user id is missing in profile response");
        }

        Object emailObj = userBody.get("email");
        String email = null;
        if (emailObj != null) {
            email = emailObj.toString();
        }

        Object displayNameObj = userBody.get("display_name");
        String displayName = "";
        if (displayNameObj != null) {
            displayName = displayNameObj.toString();
        }

        Object countryObj = userBody.get("country");
        String country = "";
        if (countryObj != null) {
            country = countryObj.toString();
        }

        Object productObj = userBody.get("product");
        String product = "";
        if (productObj != null) {
            product = productObj.toString();
        }

        String avatarUrl = "";
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> images = (List<Map<String, Object>>) userBody.get("images");
        if (images != null && !images.isEmpty()) {
            Object urlObj = images.get(0).get("url");
            if (urlObj != null) {
                avatarUrl = urlObj.toString();
            }
        }

        return new UserProfileData(email, avatarUrl, idObj.toString(), displayName, country, product);
    }

    private static class SpotifyTokenResponse {
        final String accessToken;
        final String refreshToken;
        final Integer expiresIn;

        SpotifyTokenResponse(String accessToken, String refreshToken, Integer expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }
    }

    private static class UserProfileData {
        final String email;
        final String avatarUrl;
        final String userIdentifier;
        final String displayName;
        final String country;
        final String product;

        UserProfileData(String email, String avatarUrl, String userIdentifier, String displayName,
                        String country, String product) {
            this.email = email;
            this.avatarUrl = avatarUrl;
            this.userIdentifier = userIdentifier;
            this.displayName = displayName;
            this.country = country;
            this.product = product;
        }
    }
}