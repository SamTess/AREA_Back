package area.server.AREA_Back.service;

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

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.google.client-id")
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.google.client-secret")
@Service
public class OAuthGoogleService extends OAuthService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String redirectBaseUrl;
    private final TokenEncryptionService tokenEncryptionService;
    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final UserRepository userRepository;
    private final RedisTokenService redisTokenService;
    private final MeterRegistry meterRegistry;

    private Counter oauthLoginSuccessCounter;
    private Counter oauthLoginFailureCounter;
    private Counter authenticateCalls;
    private Counter tokenExchangeCalls;
    private Counter tokenExchangeFailures;

    public OAuthGoogleService(
        @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId,
        @Value("${spring.security.oauth2.client.registration.google.client-secret}") String googleClientSecret,
        @Value("${OAUTH_REDIRECT_BASE_URL:http://localhost:3000}") String redirectBaseUrl,
        JwtService jwtService,
        MeterRegistry meterRegistry,
        RedisTokenService redisTokenService,
        PasswordEncoder passwordEncoder,
        TokenEncryptionService tokenEncryptionService,
        UserOAuthIdentityRepository userOAuthIdentityRepository,
        UserRepository userRepository
    ) {
        super(
            "google",
            "Google",
            "https://img.icons8.com/?size=100&id=17949&format=png&color=000000",
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + googleClientId
                + "&redirect_uri=" + redirectBaseUrl + "/oauth-callback"
                + "&response_type=code"
                + "&scope=openid%20email%20profile"
                + "%20https://www.googleapis.com/auth/gmail.readonly"
                + "%20https://www.googleapis.com/auth/gmail.send"
                + "%20https://www.googleapis.com/auth/gmail.modify"
                + "%20https://www.googleapis.com/auth/calendar"
                + "%20https://www.googleapis.com/auth/calendar.events"
                + "%20https://www.googleapis.com/auth/drive"
                + "%20https://www.googleapis.com/auth/drive.file"
                + "%20https://www.googleapis.com/auth/spreadsheets"
                + "&access_type=offline"
                + "&prompt=consent",
            googleClientId,
            googleClientSecret,
            jwtService
        );
        this.redirectBaseUrl = redirectBaseUrl;
        this.meterRegistry = meterRegistry;
        this.redisTokenService = redisTokenService;
        this.tokenEncryptionService = tokenEncryptionService;
        this.userOAuthIdentityRepository = userOAuthIdentityRepository;
        this.userRepository = userRepository;
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
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        authenticateCalls.increment();
        try {
            GoogleTokenResponse tokenResponse = exchangeCodeForToken(request.getAuthorizationCode());

            UserProfileData profileData = fetchUserProfile(tokenResponse.accessToken);

            if (profileData.email == null || profileData.email.isEmpty()) {
                oauthLoginFailureCounter.increment();
                throw new RuntimeException("Google account email is required for authentication");
            }

            UserOAuthIdentity oauth = handleUserAuthentication(
                profileData,
                tokenResponse.accessToken,
                tokenResponse.refreshToken,
                tokenResponse.expiresIn
            );

            oauthLoginSuccessCounter.increment();
            return generateAuthResponse(oauth, response);
        } catch (Exception e) {
            oauthLoginFailureCounter.increment();
            log.error("Google OAuth authentication failed", e);
            throw e;
        }
    }

    public UserOAuthIdentity linkToExistingUser(User existingUser, String authorizationCode) {
        GoogleTokenResponse tokenResponse = exchangeCodeForToken(authorizationCode);
        UserProfileData profileData = fetchUserProfile(tokenResponse.accessToken);

        if (profileData.email == null || profileData.email.isEmpty()) {
            throw new RuntimeException("Google account email is required for account linking");
        }

        Optional<UserOAuthIdentity> existingOAuth = userOAuthIdentityRepository
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
        Map<String, Object> tokenMeta = new HashMap<>();
        tokenMeta.put("name", profileData.name);
        tokenMeta.put("given_name", profileData.givenName);
        tokenMeta.put("family_name", profileData.familyName);
        tokenMeta.put("picture", profileData.picture);
        tokenMeta.put("locale", profileData.locale);
        oauth.setTokenMeta(tokenMeta);

        try {
            oauth = this.userOAuthIdentityRepository.save(oauth);
            log.info("Successfully linked Google account {} to user {}",
                    profileData.userIdentifier, existingUser.getId());
            return oauth;
        } catch (Exception e) {
            log.error("Failed to link Google account to existing user", e);
            throw new RuntimeException("Failed to link Google account: " + e.getMessage(), e);
        }
    }

    private GoogleTokenResponse exchangeCodeForToken(String authorizationCode) {
        tokenExchangeCalls.increment();
        try {
            String tokenUrl = "https://oauth2.googleapis.com/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", authorizationCode);
            body.add("client_id", this.clientId);
            body.add("client_secret", this.clientSecret);
            body.add("redirect_uri", this.redirectBaseUrl + "/oauth-callback");
            body.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> tokenResponse = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("Failed to exchange token with Google");
            }

            Map<String, Object> responseBody = tokenResponse.getBody();
            String accessToken = (String) responseBody.get("access_token");
            String refreshToken = (String) responseBody.get("refresh_token");
            Integer expiresIn = (Integer) responseBody.get("expires_in");

            if (accessToken == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("No access_token in Google response");
            }

            return new GoogleTokenResponse(accessToken, refreshToken, expiresIn);
        } catch (Exception e) {
            tokenExchangeFailures.increment();
            log.error("Failed to exchange authorization code for token", e);
            throw new RuntimeException("Token exchange failed: " + e.getMessage(), e);
        }
    }

    private UserProfileData fetchUserProfile(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> userResponse = restTemplate.exchange(
            "https://www.googleapis.com/oauth2/v2/userinfo",
            HttpMethod.GET,
            request,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        if (!userResponse.getStatusCode().is2xxSuccessful() || userResponse.getBody() == null) {
            throw new RuntimeException("Failed to fetch Google user profile");
        }

        Map<String, Object> userBody = userResponse.getBody();

        String email = (String) userBody.get("email");
        String userIdentifier = (String) userBody.get("id");
        String name = (String) userBody.getOrDefault("name", "");
        String givenName = (String) userBody.getOrDefault("given_name", "");
        String familyName = (String) userBody.getOrDefault("family_name", "");
        String picture = (String) userBody.getOrDefault("picture", "");
        String locale = (String) userBody.getOrDefault("locale", "");
        Boolean verifiedEmail = (Boolean) userBody.getOrDefault("verified_email", false);

        if (userIdentifier == null) {
            throw new RuntimeException("Google user id is missing in profile response");
        }

        return new UserProfileData(email, userIdentifier, name, givenName, familyName, picture, locale, verifiedEmail);
    }

    private UserOAuthIdentity handleUserAuthentication(
            UserProfileData profileData,
            String accessToken,
            String refreshToken,
            Integer expiresIn) {

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
            this.userRepository.save(user);
        } else {
            user = new User();
            user.setEmail(profileData.email);
            user.setIsActive(true);
            user.setIsAdmin(false);
            user.setCreatedAt(LocalDateTime.now());
            if (profileData.picture != null && !profileData.picture.isEmpty()) {
                user.setAvatarUrl(profileData.picture);
            }
            user = this.userRepository.save(user);
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

        Map<String, Object> tokenMeta = new HashMap<>();
        tokenMeta.put("name", profileData.name);
        tokenMeta.put("given_name", profileData.givenName);
        tokenMeta.put("family_name", profileData.familyName);
        tokenMeta.put("picture", profileData.picture);
        tokenMeta.put("locale", profileData.locale);
        tokenMeta.put("verified_email", profileData.verifiedEmail);
        oauth.setTokenMeta(tokenMeta);

        try {
            oauth = this.userOAuthIdentityRepository.save(oauth);
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
                    oauth = this.userOAuthIdentityRepository.save(oauth);
                } else {
                    throw new RuntimeException("Failed to handle OAuth identity: " + e.getMessage(), e);
                }
            } else {
                throw new RuntimeException("Database error during OAuth identity save: " + e.getMessage(), e);
            }
        }

        return oauth;
    }

    private AuthResponse generateAuthResponse(UserOAuthIdentity oauth, HttpServletResponse response) {
        User user = oauth.getUser();
        UserResponse userResponse = new UserResponse();

        userResponse.setId(user.getId());
        userResponse.setEmail(user.getEmail());
        userResponse.setIsActive(user.getIsActive());
        userResponse.setIsAdmin(user.getIsAdmin());
        userResponse.setCreatedAt(user.getCreatedAt());
        userResponse.setLastLoginAt(user.getLastLoginAt());
        userResponse.setAvatarUrl(user.getAvatarUrl());

        String accessToken;
        String refreshToken;
        try {
            accessToken = this.jwtService.generateAccessToken(userResponse.getId(), userResponse.getEmail());
            refreshToken = this.jwtService.generateRefreshToken(userResponse.getId(), userResponse.getEmail());
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

        return new AuthResponse(
            "Login successful",
            userResponse
        );
    }

    private LocalDateTime calculateExpirationTime(Integer expiresIn) {
        if (expiresIn == null) {
            return LocalDateTime.now().plusHours(1);
        }
        return LocalDateTime.now().plusSeconds(expiresIn);
    }

    private static class GoogleTokenResponse {
        String accessToken;
        String refreshToken;
        Integer expiresIn;

        GoogleTokenResponse(String accessToken, String refreshToken, Integer expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }
    }

    private static class UserProfileData {
        String email;
        String userIdentifier;
        String name;
        String givenName;
        String familyName;
        String picture;
        String locale;
        Boolean verifiedEmail;

        UserProfileData(String email, String userIdentifier, String name, String givenName,
                       String familyName, String picture, String locale, Boolean verifiedEmail) {
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
