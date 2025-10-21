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

@Slf4j
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.slack.client-id")
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.slack.client-secret")
@Service
public class OAuthSlackService extends OAuthService {

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

    @SuppressWarnings("ParameterNumber")
    public OAuthSlackService(
        @Value("${spring.security.oauth2.client.registration.slack.client-id}") final String slackClientId,
        @Value("${spring.security.oauth2.client.registration.slack.client-secret}")
        final String slackClientSecret,
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
        super(
            "slack",
            "Slack",
            "https://cdn.simpleicons.org/slack/4A154B",
            "https://slack.com/oauth/v2/authorize?client_id=" + slackClientId
                + "&redirect_uri=" + redirectBaseUrl + "/oauth-callback"
                + "&user_scope=identity.basic,identity.email,identity.avatar"
                + "&scope=channels:history,channels:read,chat:write"
                + ",reactions:write,reactions:read,users:read"
                + ",files:read,pins:write,im:read,im:write,im:history",
            slackClientId,
            slackClientSecret,
            jwtService,
            jwtCookieProperties
        );
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
        this.oauthLoginSuccessCounter = Counter.builder("oauth.slack.login.success")
            .description("Number of successful Slack OAuth logins")
            .register(meterRegistry);

        this.oauthLoginFailureCounter = Counter.builder("oauth.slack.login.failure")
            .description("Number of failed Slack OAuth logins")
            .register(meterRegistry);

        this.authenticateCalls = Counter.builder("oauth.slack.authenticate.calls")
            .description("Total number of Slack OAuth authenticate calls")
            .register(meterRegistry);

        this.tokenExchangeCalls = Counter.builder("oauth.slack.token_exchange.calls")
            .description("Total number of Slack token exchange calls")
            .register(meterRegistry);

        this.tokenExchangeFailures = Counter.builder("oauth.slack.token_exchange.failures")
            .description("Total number of Slack token exchange failures")
            .register(meterRegistry);
    }

    @Override
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        authenticateCalls.increment();
        try {
            log.info("Starting Slack OAuth authentication");
            SlackTokenResponse tokenResponse = exchangeCodeForToken(request.getAuthorizationCode());
            log.info("Successfully exchanged code for Slack access token");

            UserProfileData profileData = fetchUserProfile(tokenResponse.accessToken);
            log.info("Fetched Slack user profile: {}", profileData.userIdentifier);

            if (profileData.email == null || profileData.email.isEmpty()) {
                oauthLoginFailureCounter.increment();
                throw new RuntimeException("Slack account email is required for authentication");
            }

            log.info("Calling authService.oauthLogin for email: {}", profileData.email);
            AuthResponse authResponse = authService.oauthLogin(profileData.email, profileData.avatarUrl, response);
            log.info("AuthService.oauthLogin completed successfully for user: {}", authResponse.getUser().getId());

            User user = userRepository.findById(authResponse.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found after login"));

            UserOAuthIdentity oauth = handleUserAuthentication(
                user,
                profileData,
                tokenResponse
            );

            userOAuthIdentityRepository.save(oauth);
            log.info("Saved OAuth identity successfully");

            oauthLoginSuccessCounter.increment();
            log.info("Slack OAuth authentication completed successfully for user: {}", user.getId());
            return authResponse;
        } catch (Exception e) {
            log.error("Slack OAuth authentication failed", e);
            oauthLoginFailureCounter.increment();
            throw new RuntimeException("OAuth authentication failed: " + e.getMessage(), e);
        }
    }

    public UserOAuthIdentity linkToExistingUser(User existingUser, String authorizationCode) {
        SlackTokenResponse tokenResponse = exchangeCodeForToken(authorizationCode);
        UserProfileData profileData = fetchUserProfile(tokenResponse.accessToken);

        if (profileData.email == null || profileData.email.isEmpty()) {
            throw new RuntimeException("Slack account email is required for account linking");
        }

        Optional<UserOAuthIdentity> existingOAuth = userOAuthIdentityRepository
            .findByProviderAndProviderUserId(this.providerKey, profileData.userIdentifier);

        if (existingOAuth.isPresent() && !existingOAuth.get().getUser().getId().equals(existingUser.getId())) {
            throw new RuntimeException("This Slack account is already linked to another user");
        }

        Optional<UserOAuthIdentity> userOAuthOpt = userOAuthIdentityRepository
            .findByUserAndProvider(existingUser, this.providerKey);

        UserOAuthIdentity oauth;
        if (userOAuthOpt.isPresent()) {
            oauth = userOAuthOpt.get();
            updateOAuthIdentity(oauth, profileData, tokenResponse);
        } else {
            oauth = createOAuthIdentity(existingUser, profileData, tokenResponse);
        }

        try {
            oauth = userOAuthIdentityRepository.save(oauth);
            log.info("Successfully linked Slack account {} to user {}",
                    profileData.userIdentifier, existingUser.getId());
            return oauth;
        } catch (Exception e) {
            log.error("Failed to link Slack account to existing user", e);
            throw new RuntimeException("Failed to link Slack account: " + e.getMessage(), e);
        }
    }

    private UserOAuthIdentity handleUserAuthentication(
            User user,
            UserProfileData profileData,
            SlackTokenResponse tokenResponse) {

        Optional<UserOAuthIdentity> oauthOpt = userOAuthIdentityRepository.findByUserAndProvider(user,
            this.providerKey);

        UserOAuthIdentity oauth;
        if (oauthOpt.isPresent()) {
            oauth = oauthOpt.get();
            updateOAuthIdentity(oauth, profileData, tokenResponse);
            log.info("Updated existing OAuth identity for user: {}", user.getId());
        } else {
            oauth = createOAuthIdentity(user, profileData, tokenResponse);
            log.info("Created new OAuth identity for user: {}", user.getId());
        }

        return oauth;
    }

    private UserOAuthIdentity createOAuthIdentity(
            User user,
            UserProfileData profileData,
            SlackTokenResponse tokenResponse) {

        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setUser(user);
        oauth.setProvider(this.providerKey);
        oauth.setProviderUserId(profileData.userIdentifier);
        oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(tokenResponse.accessToken));

        if (tokenResponse.refreshToken != null && !tokenResponse.refreshToken.isEmpty()) {
            oauth.setRefreshTokenEnc(tokenEncryptionService.encryptToken(tokenResponse.refreshToken));
        }

        oauth.setExpiresAt(calculateExpirationTime(tokenResponse.expiresIn));
        oauth.setCreatedAt(LocalDateTime.now());
        oauth.setUpdatedAt(LocalDateTime.now());
        oauth.setTokenMeta(buildTokenMeta(profileData, tokenResponse));

        return oauth;
    }

    private void updateOAuthIdentity(
            UserOAuthIdentity oauth,
            UserProfileData profileData,
            SlackTokenResponse tokenResponse) {

        oauth.setProviderUserId(profileData.userIdentifier);
        oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(tokenResponse.accessToken));

        if (tokenResponse.refreshToken != null && !tokenResponse.refreshToken.isEmpty()) {
            oauth.setRefreshTokenEnc(tokenEncryptionService.encryptToken(tokenResponse.refreshToken));
        }

        oauth.setExpiresAt(calculateExpirationTime(tokenResponse.expiresIn));
        oauth.setUpdatedAt(LocalDateTime.now());
        oauth.setTokenMeta(buildTokenMeta(profileData, tokenResponse));
    }

    private Map<String, Object> buildTokenMeta(UserProfileData profileData, SlackTokenResponse tokenResponse) {
        Map<String, Object> tokenMeta = new HashMap<>();
        tokenMeta.put("display_name", profileData.displayName);
        tokenMeta.put("real_name", profileData.realName);
        tokenMeta.put("avatar_url", profileData.avatarUrl);
        tokenMeta.put("team_id", tokenResponse.teamId);
        tokenMeta.put("team_name", tokenResponse.teamName);
        return tokenMeta;
    }

    private LocalDateTime calculateExpirationTime(Integer expiresIn) {
        if (expiresIn == null || expiresIn <= 0) {
            return LocalDateTime.now().plusDays(90);
        }
        return LocalDateTime.now().plusSeconds(expiresIn);
    }

    private SlackTokenResponse exchangeCodeForToken(String authorizationCode) {
        tokenExchangeCalls.increment();
        try {
            String tokenUrl = "https://slack.com/api/oauth.v2.access";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", this.clientId);
            body.add("client_secret", this.clientSecret);
            body.add("code", authorizationCode);
            body.add("redirect_uri", this.redirectBaseUrl + "/oauth-callback");

            HttpEntity<MultiValueMap<String, String>> slackRequest = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> tokenResponse = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                slackRequest,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("Failed to exchange token with Slack");
            }

            Map<String, Object> responseBody = tokenResponse.getBody();

            Object okObj = responseBody.get("ok");
            if (okObj == null || !Boolean.TRUE.equals(okObj)) {
                tokenExchangeFailures.increment();
                Object errorObj = responseBody.get("error");
                String errorMsg = errorObj != null ? errorObj.toString() : "Unknown error";
                throw new RuntimeException("Slack API returned error: " + errorMsg);
            }

            Object authedUserObj = responseBody.get("authed_user");
            if (!(authedUserObj instanceof Map)) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("No authed_user in Slack response");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> authedUser = (Map<String, Object>) authedUserObj;
            Object accessTokenObj = authedUser.get("access_token");
            if (accessTokenObj == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("No access_token in Slack response");
            }

            Object refreshTokenObj = authedUser.get("refresh_token");
            String refreshToken = refreshTokenObj != null ? refreshTokenObj.toString() : null;

            Object expiresInObj = authedUser.get("expires_in");
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

            Object teamObj = responseBody.get("team");
            String teamId = null;
            String teamName = null;
            if (teamObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> team = (Map<String, Object>) teamObj;
                Object teamIdObj = team.get("id");
                Object teamNameObj = team.get("name");
                teamId = teamIdObj != null ? teamIdObj.toString() : null;
                teamName = teamNameObj != null ? teamNameObj.toString() : null;
            }

            return new SlackTokenResponse(
                accessTokenObj.toString(),
                refreshToken,
                expiresIn,
                teamId,
                teamName
            );
        } catch (Exception e) {
            tokenExchangeFailures.increment();
            log.error("Token exchange failed", e);
            throw new RuntimeException("Failed to exchange authorization code: " + e.getMessage(), e);
        }
    }

    private UserProfileData fetchUserProfile(String slackAccessToken) {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(slackAccessToken);
        authHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> authRequest = new HttpEntity<>(authHeaders);

        ResponseEntity<Map<String, Object>> userResp = restTemplate.exchange(
            "https://slack.com/api/users.identity",
            HttpMethod.GET,
            authRequest,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        if (!userResp.getStatusCode().is2xxSuccessful() || userResp.getBody() == null) {
            throw new RuntimeException("Failed to fetch Slack user profile");
        }

        Map<String, Object> responseBody = userResp.getBody();

        Object okObj = responseBody.get("ok");
        if (okObj == null || !Boolean.TRUE.equals(okObj)) {
            Object errorObj = responseBody.get("error");
            String errorMsg = errorObj != null ? errorObj.toString() : "Unknown error";
            throw new RuntimeException("Slack API returned error: " + errorMsg);
        }

        Object userObj = responseBody.get("user");
        if (!(userObj instanceof Map)) {
            throw new RuntimeException("No user object in Slack identity response");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) userObj;

        Object idObj = user.get("id");
        if (idObj == null) {
            throw new RuntimeException("Slack user id is missing in profile response");
        }

        Object emailObj = user.get("email");
        String email = emailObj != null ? emailObj.toString() : null;

        Object nameObj = user.get("name");
        String displayName = nameObj != null ? nameObj.toString() : "";

        String realName = "";
        String avatarUrl = "";

        Object profileObj = user.get("profile");
        if (profileObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) profileObj;

            Object realNameObj = profile.get("real_name");
            realName = realNameObj != null ? realNameObj.toString() : "";

            Object imageObj = profile.get("image_512");
            if (imageObj == null) {
                imageObj = profile.get("image_192");
            }
            if (imageObj == null) {
                imageObj = profile.get("image_72");
            }
            avatarUrl = imageObj != null ? imageObj.toString() : "";
        }

        return new UserProfileData(
            email,
            avatarUrl,
            idObj.toString(),
            displayName,
            realName
        );
    }

    private static class SlackTokenResponse {
        final String accessToken;
        final String refreshToken;
        final Integer expiresIn;
        final String teamId;
        final String teamName;

        SlackTokenResponse(String accessToken, String refreshToken, Integer expiresIn,
                          String teamId, String teamName) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
            this.teamId = teamId;
            this.teamName = teamName;
        }
    }

    private static class UserProfileData {
        final String email;
        final String avatarUrl;
        final String userIdentifier;
        final String displayName;
        final String realName;

        UserProfileData(String email, String avatarUrl, String userIdentifier,
                       String displayName, String realName) {
            this.email = email;
            this.avatarUrl = avatarUrl;
            this.userIdentifier = userIdentifier;
            this.displayName = displayName;
            this.realName = realName;
        }
    }
}
