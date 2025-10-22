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
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import area.server.AREA_Back.service.Webhook.GoogleWatchService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(
    prefix = "spring.security.oauth2.client.registration.google",
    name = { "client-id", "client-secret" }
)
public class OAuthGoogleService extends OAuthService {

    private final RestTemplate restTemplate;
    private final String redirectBaseUrl;
    private final TokenEncryptionService tokenEncryptionService;
    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final UserRepository userRepository;
    private final RedisTokenService redisTokenService;
    private final MeterRegistry meterRegistry;
    private final GoogleWatchService googleWatchService;

    private Counter oauthLoginSuccessCounter;
    private Counter oauthLoginFailureCounter;
    private Counter authenticateCalls;
    private Counter tokenExchangeCalls;
    private Counter tokenExchangeFailures;

    /**
     * Constructs OAuth Google Service with dependencies
     */
    @SuppressWarnings("ParameterNumber")
    public OAuthGoogleService(
        @Value("${spring.security.oauth2.client.registration.google.client-id}") final String googleClientId,
        @Value("${spring.security.oauth2.client.registration.google.client-secret}") final String googleClientSecret,
        @Value("${OAUTH_REDIRECT_BASE_URL:http://localhost:3000}") final String redirectBaseUrl,
        final JwtService jwtService,
        final JwtCookieProperties jwtCookieProperties,
        final MeterRegistry meterRegistry,
        final RedisTokenService redisTokenService,
        final PasswordEncoder passwordEncoder, // kept for DI symmetry
        final TokenEncryptionService tokenEncryptionService,
        final UserOAuthIdentityRepository userOAuthIdentityRepository,
        final UserRepository userRepository,
        final RestTemplate restTemplate,
        final GoogleWatchService googleWatchService
    ) {
        super(
            "google",
            "Google",
            "https://img.icons8.com/?size=100&id=17949&format=png&color=000000",
            "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + googleClientId
                + "&redirect_uri=" + redirectBaseUrl + "/oauth-callback"
                + "&response_type=code"
                + "&scope=openid%20email%20profile"
                + "%20https://www.googleapis.com/auth/gmail.readonly"
                + "%20https://www.googleapis.com/auth/gmail.send"
                + "%20https://www.googleapis.com/auth/gmail.modify"
                + "&access_type=offline"
                + "&prompt=consent",
            googleClientId,
            googleClientSecret,
            jwtService,
            jwtCookieProperties
        );
        this.redirectBaseUrl = redirectBaseUrl;
        this.meterRegistry = meterRegistry;
        this.redisTokenService = redisTokenService;
        this.tokenEncryptionService = tokenEncryptionService;
        this.userOAuthIdentityRepository = userOAuthIdentityRepository;
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
        this.googleWatchService = googleWatchService;
    }

    @PostConstruct
    private void initMetrics() {
        this.oauthLoginSuccessCounter = Counter.builder("oauth.google.login.success")
            .description("Number of successful Google OAuth logins")
            .register(meterRegistry);

        this.oauthLoginFailureCounter = Counter.builder("oauth.google.login.failure")
            .description("Number of failed Google OAuth logins")
            .register(meterRegistry);

        this.authenticateCalls = Counter.builder("oauth.google.authenticate.calls")
            .description("Total number of Google OAuth authenticate calls")
            .register(meterRegistry);

        this.tokenExchangeCalls = Counter.builder("oauth.google.token_exchange.calls")
            .description("Total number of Google token exchange calls")
            .register(meterRegistry);

        this.tokenExchangeFailures = Counter.builder("oauth.google.token_exchange.failures")
            .description("Total number of Google token exchange failures")
            .register(meterRegistry);
    }

    @Override
    public AuthResponse authenticate(final OAuthLoginRequest request, final HttpServletResponse response) {
        authenticateCalls.increment();
        try {
            log.info("Starting Google OAuth authentication");
            final GoogleTokenResponse tokenResponse = exchangeCodeForToken(request.getAuthorizationCode());
            log.info("Successfully exchanged code for Google tokens");

            final UserProfileData profileData = fetchUserProfile(tokenResponse.accessToken);
            log.info("Fetched Google user profile (email={}, id={})", profileData.email, profileData.userIdentifier);

            if (profileData.email == null || profileData.email.isEmpty()) {
                oauthLoginFailureCounter.increment();
                throw new RuntimeException("Google account email is required for authentication");
            }

            final UserOAuthIdentity oauth = handleUserAuthentication(
                profileData,
                tokenResponse.accessToken,
                tokenResponse.refreshToken,
                tokenResponse.expiresIn
            );

            final AuthResponse out = generateAuthResponse(oauth, response);

            // Optionnel : démarrage auto du watch Gmail
            try {
                googleWatchService.startGmailWatch(oauth.getUser().getId());
                log.info("Auto-started Gmail watch for user {}", oauth.getUser().getId());
            } catch (Exception e) {
                log.warn("Failed to auto-start Gmail watch for user {}: {}", oauth.getUser().getId(), e.getMessage());
            }

            oauthLoginSuccessCounter.increment();
            log.info("Google OAuth authentication completed successfully for user {}", oauth.getUser().getId());
            return out;

        } catch (Exception e) {
            oauthLoginFailureCounter.increment();
            log.error("Google OAuth authentication failed", e);
            throw new RuntimeException("OAuth authentication failed: " + e.getMessage(), e);
        }
    }

    public UserOAuthIdentity linkToExistingUser(final User existingUser, final String authorizationCode) {
        final GoogleTokenResponse tokenResponse = exchangeCodeForToken(authorizationCode);
        final UserProfileData profileData = fetchUserProfile(tokenResponse.accessToken);

        if (profileData.email == null || profileData.email.isEmpty()) {
            throw new RuntimeException("Google account email is required for account linking");
        }

        final Optional<UserOAuthIdentity> existingOAuth = userOAuthIdentityRepository
            .findByProviderAndProviderUserId(this.providerKey, profileData.userIdentifier);

        if (existingOAuth.isPresent() && !existingOAuth.get().getUser().getId().equals(existingUser.getId())) {
            throw new RuntimeException("This Google account is already linked to another user");
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
        final Map<String, Object> tokenMeta = new HashMap<>();
        tokenMeta.put("name", profileData.name);
        tokenMeta.put("given_name", profileData.givenName);
        tokenMeta.put("family_name", profileData.familyName);
        tokenMeta.put("picture", profileData.picture);
        tokenMeta.put("locale", profileData.locale);
        tokenMeta.put("verified_email", profileData.verifiedEmail);
        oauth.setTokenMeta(tokenMeta);

        try {
            oauth = this.userOAuthIdentityRepository.save(oauth);
            log.info(
                "Successfully linked Google account {} to user {}",
                profileData.userIdentifier,
                existingUser.getId()
            );

            try {
                googleWatchService.startGmailWatch(existingUser.getId());
                log.info("Auto-started Gmail watch for linked user {}", existingUser.getId());
            } catch (Exception e) {
                log.warn(
                    "Failed to auto-start Gmail watch for linked user {}: {}",
                    existingUser.getId(),
                    e.getMessage()
                );
            }

            return oauth;
        } catch (Exception e) {
            log.error("Failed to link Google account to existing user", e);
            throw new RuntimeException("Failed to link Google account: " + e.getMessage(), e);
        }
    }

    private GoogleTokenResponse exchangeCodeForToken(final String authorizationCode) {
        tokenExchangeCalls.increment();
        try {
            final String tokenUrl = "https://oauth2.googleapis.com/token";

            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("User-Agent", "AREA-Backend/1.0");
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", authorizationCode);
            body.add("client_id", this.clientId);
            body.add("client_secret", this.clientSecret);
            body.add("redirect_uri", this.redirectBaseUrl + "/oauth-callback");
            body.add("grant_type", "authorization_code");

            final HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            final ResponseEntity<Map<String, Object>> tokenResponse = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("Failed to exchange token with Google (http "
                    + tokenResponse.getStatusCode().value() + ")");
            }

            final Map<String, Object> responseBody = tokenResponse.getBody();
            if (responseBody == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("Google token response body is null");
            }
            final String accessToken = (String) responseBody.get("access_token");
            final String refreshToken = (String) responseBody.get("refresh_token");
            final Integer expiresIn;
            if (responseBody.get("expires_in") instanceof Number) {
                expiresIn = ((Number) responseBody.get("expires_in")).intValue();
            } else {
                expiresIn = null;
            }

            if (accessToken == null) {
                tokenExchangeFailures.increment();
                final Object err = responseBody.get("error_description");
                final String desc;
                if (err != null) {
                    desc = err.toString();
                } else {
                    desc = "No access_token in Google response";
                }
                throw new RuntimeException(desc);
            }

            return new GoogleTokenResponse(accessToken, refreshToken, expiresIn);
        } catch (Exception e) {
            tokenExchangeFailures.increment();
            log.error("Failed to exchange authorization code for Google token", e);
            throw new RuntimeException("Token exchange failed: " + e.getMessage(), e);
        }
    }

    private UserProfileData fetchUserProfile(final String accessToken) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("User-Agent", "AREA-Backend/1.0");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        final HttpEntity<Void> request = new HttpEntity<>(headers);

        final ResponseEntity<Map<String, Object>> userResponse = restTemplate.exchange(
            "https://www.googleapis.com/oauth2/v2/userinfo",
            HttpMethod.GET,
            request,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        if (!userResponse.getStatusCode().is2xxSuccessful() || userResponse.getBody() == null) {
            throw new RuntimeException("Failed to fetch Google user profile");
        }

        final Map<String, Object> userBody = userResponse.getBody();
        if (userBody == null) {
            throw new RuntimeException("Google user profile response body is null");
        }

        final String email = (String) userBody.get("email");
        final String userIdentifier = (String) userBody.get("id");
        final String name = (String) userBody.getOrDefault("name", "");
        final String givenName = (String) userBody.getOrDefault("given_name", "");
        final String familyName = (String) userBody.getOrDefault("family_name", "");
        final String picture = (String) userBody.getOrDefault("picture", "");
        final String locale = (String) userBody.getOrDefault("locale", "");
        final Boolean verifiedEmail = (Boolean) userBody.getOrDefault("verified_email", Boolean.FALSE);

        if (userIdentifier == null) {
            throw new RuntimeException("Google user id is missing in profile response");
        }

        return new UserProfileData(email, userIdentifier, name, givenName, familyName, picture, locale, verifiedEmail);
    }

    private UserOAuthIdentity handleUserAuthentication(
        final UserProfileData profileData,
        final String accessToken,
        final String refreshToken,
        final Integer expiresIn
    ) {
        Optional<User> userOpt = Optional.empty();
        if (profileData.email != null && !profileData.email.isEmpty()) {
            userOpt = userRepository.findByEmail(profileData.email);
        }

        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            user.setLastLoginAt(LocalDateTime.now());
            user.setEmail(profileData.email);
            if (profileData.picture != null && !profileData.picture.isEmpty()) {
                user.setAvatarUrl(profileData.picture);
            }
            userRepository.save(user);
        } else {
            user = new User();
            user.setEmail(profileData.email);
            user.setIsActive(true);
            user.setIsAdmin(false);
            user.setCreatedAt(LocalDateTime.now());
            if (profileData.picture != null && !profileData.picture.isEmpty()) {
                user.setAvatarUrl(profileData.picture);
            }
            user = userRepository.save(user);
        }

        Optional<UserOAuthIdentity> oauthOpt = userOAuthIdentityRepository
            .findByUserAndProvider(user, this.providerKey);

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
        }

        final Map<String, Object> tokenMeta = new HashMap<>();
        tokenMeta.put("name", profileData.name);
        tokenMeta.put("given_name", profileData.givenName);
        tokenMeta.put("family_name", profileData.familyName);
        tokenMeta.put("picture", profileData.picture);
        tokenMeta.put("locale", profileData.locale);
        tokenMeta.put("verified_email", profileData.verifiedEmail);
        oauth.setTokenMeta(tokenMeta);

        try {
            oauth = userOAuthIdentityRepository.save(oauth);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                oauthOpt = userOAuthIdentityRepository.findByUserAndProvider(user, this.providerKey);
                if (oauthOpt.isPresent()) {
                    oauth = oauthOpt.get();
                    oauth.setProviderUserId(profileData.userIdentifier);
                    oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(accessToken));
                    if (refreshToken != null && !refreshToken.isEmpty()) {
                        oauth.setRefreshTokenEnc(tokenEncryptionService.encryptToken(refreshToken));
                    }
                    oauth.setExpiresAt(calculateExpirationTime(expiresIn));
                    oauth.setUpdatedAt(LocalDateTime.now());
                    oauth.setTokenMeta(tokenMeta);
                    oauth = userOAuthIdentityRepository.save(oauth);
                } else {
                    throw new RuntimeException("Failed to handle OAuth identity: " + e.getMessage(), e);
                }
            } else {
                throw new RuntimeException("Database error during OAuth identity save: " + e.getMessage(), e);
            }
        }

        return oauth;
    }

    private AuthResponse generateAuthResponse(final UserOAuthIdentity oauth, final HttpServletResponse response) {
        final User user = oauth.getUser();
        final UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setEmail(user.getEmail());
        userResponse.setIsActive(user.getIsActive());
        userResponse.setIsAdmin(user.getIsAdmin());
        userResponse.setCreatedAt(user.getCreatedAt());
        userResponse.setLastLoginAt(user.getLastLoginAt());
        userResponse.setAvatarUrl(user.getAvatarUrl());

        final String accessToken;
        final String refreshToken;
        try {
            accessToken = jwtService.generateAccessToken(userResponse.getId(), userResponse.getEmail());
            refreshToken = jwtService.generateRefreshToken(userResponse.getId(), userResponse.getEmail());
        } catch (Exception e) {
            log.error("JWT token generation failed", e);
            throw new RuntimeException("JWT token generation failed: " + e.getMessage(), e);
        }

        try {
            redisTokenService.storeAccessToken(accessToken, user.getId());
            redisTokenService.storeRefreshToken(user.getId(), refreshToken);
        } catch (Exception e) {
            log.error("Redis token storage failed", e);
            throw new RuntimeException("Redis token storage failed: " + e.getMessage(), e);
        }

        try {
            setTokenCookies(response, accessToken, refreshToken);
        } catch (Exception e) {
            log.error("Cookie setting failed", e);
            throw new RuntimeException("Cookie setting failed: " + e.getMessage(), e);
        }

        return new AuthResponse("Login successful", userResponse);
    }

    private LocalDateTime calculateExpirationTime(final Integer expiresIn) {
        if (expiresIn == null) {
            return LocalDateTime.now().plusHours(1);
        }
        return LocalDateTime.now().plusSeconds(expiresIn);
    }

    private static class GoogleTokenResponse {
        final String accessToken;
        final String refreshToken;
        final Integer expiresIn;

        GoogleTokenResponse(final String accessToken, final String refreshToken, final Integer expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }
    }

    private static class UserProfileData {
        final String email;
        final String userIdentifier;
        final String name;
        final String givenName;
        final String familyName;
        final String picture;
        final String locale;
        final Boolean verifiedEmail;

        @SuppressWarnings("ParameterNumber")
        UserProfileData(
            final String email,
            final String userIdentifier,
            final String name,
            final String givenName,
            final String familyName,
            final String picture,
            final String locale,
            final Boolean verifiedEmail
        ) {
            this.email = email;
            this.userIdentifier = userIdentifier;
            this.name = name;
            this.givenName = givenName;
            this.familyName = familyName;
            this.picture = picture;
            this.locale = locale;
            this.verifiedEmail = verifiedEmail;
        }
    }
}
