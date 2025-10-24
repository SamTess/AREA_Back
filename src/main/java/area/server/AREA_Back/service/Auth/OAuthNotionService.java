package area.server.AREA_Back.service.Auth;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
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
 * OAuth service implementation for Notion integration.
 * Handles OAuth2 authentication flow with Notion API.
 */
@Slf4j
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.notion.client-id")
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.notion.client-secret")
@Service
public class OAuthNotionService extends OAuthService {

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

    /**
     * Constructs OAuth Notion Service with dependencies.
     *
     * @param notionClientId Notion OAuth2 client ID
     * @param notionClientSecret Notion OAuth2 client secret
     * @param redirectBaseUrl Base URL for OAuth redirects
     * @param jwtService JWT token service
     * @param jwtCookieProperties JWT cookie configuration
     * @param meterRegistry Metrics registry
     * @param redisTokenService Redis token service
     * @param passwordEncoder Password encoder
     * @param tokenEncryptionService Token encryption service
     * @param userOAuthIdentityRepository OAuth identity repository
     * @param userRepository User repository
     * @param authService Authentication service
     * @param restTemplate Configured RestTemplate bean
     */
    @SuppressWarnings({"ParameterNumber", "checkstyle:ParameterNumber"})
    public OAuthNotionService(
        @Value("${spring.security.oauth2.client.registration.notion.client-id}") final String notionClientId,
        @Value("${spring.security.oauth2.client.registration.notion.client-secret}")
        final String notionClientSecret,
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
            "notion",
            "Notion",
            "https://cdn.simpleicons.org/notion/000000",
            "https://api.notion.com/v1/oauth/authorize?client_id=" + notionClientId
                + "&redirect_uri=" + redirectBaseUrl + "/oauth-callback"
                + "&response_type=code"
                + "&owner=user",
            notionClientId,
            notionClientSecret,
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
        this.oauthLoginSuccessCounter = Counter.builder("oauth.notion.login.success")
            .description("Number of successful Notion OAuth logins")
            .register(meterRegistry);

        this.oauthLoginFailureCounter = Counter.builder("oauth.notion.login.failure")
            .description("Number of failed Notion OAuth logins")
            .register(meterRegistry);

        this.authenticateCalls = Counter.builder("oauth.notion.authenticate.calls")
            .description("Total number of Notion OAuth authenticate calls")
            .register(meterRegistry);

        this.tokenExchangeCalls = Counter.builder("oauth.notion.token_exchange.calls")
            .description("Total number of Notion token exchange calls")
            .register(meterRegistry);

        this.tokenExchangeFailures = Counter.builder("oauth.notion.token_exchange.failures")
            .description("Total number of Notion token exchange failures")
            .register(meterRegistry);
    }

    @Override
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        authenticateCalls.increment();
        try {
            log.info("Starting Notion OAuth authentication");
            NotionTokenResponse tokenResponse = exchangeCodeForToken(request.getAuthorizationCode());
            log.info("Successfully exchanged code for Notion access token");

            UserProfileData profileData = tokenResponse.ownerProfile;
            log.info("Fetched Notion user profile: {}", profileData.userIdentifier);

            String email = profileData.email;
            if (email == null || email.isBlank()) {
                oauthLoginFailureCounter.increment();
                throw new RuntimeException("Notion account email is required for authentication");
            }

            log.info("Calling authService.oauthLogin for email: {}", email);
            AuthResponse authResponse = authService.oauthLogin(email, profileData.avatarUrl, response);
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
            log.info("Notion OAuth authentication completed successfully for user: {}", user.getId());
            return authResponse;
        } catch (Exception e) {
            log.error("Notion OAuth authentication failed", e);
            oauthLoginFailureCounter.increment();
            throw new RuntimeException("OAuth authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Links a Notion account to an existing user.
     *
     * @param existingUser The user to link the Notion account to
     * @param authorizationCode The authorization code from Notion
     * @return The created or updated UserOAuthIdentity
     */
    public UserOAuthIdentity linkToExistingUser(User existingUser, String authorizationCode) {
        NotionTokenResponse tokenResponse = exchangeCodeForToken(authorizationCode);
        UserProfileData profileData = tokenResponse.ownerProfile;

        String email = profileData.email;
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Notion account email is required for account linking");
        }

        Optional<UserOAuthIdentity> existingOAuth = userOAuthIdentityRepository
            .findByProviderAndProviderUserId(this.providerKey, profileData.userIdentifier);

        if (existingOAuth.isPresent() && !existingOAuth.get().getUser().getId().equals(existingUser.getId())) {
            throw new RuntimeException("This Notion account is already linked to another user");
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
            log.info("Successfully linked Notion account {} to user {}",
                    profileData.userIdentifier, existingUser.getId());
            return oauth;
        } catch (Exception e) {
            log.error("Failed to link Notion account to existing user", e);
            throw new RuntimeException("Failed to link Notion account: " + e.getMessage(), e);
        }
    }

    private UserOAuthIdentity handleUserAuthentication(
            User user,
            UserProfileData profileData,
            NotionTokenResponse tokenResponse) {

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
            NotionTokenResponse tokenResponse) {

        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setUser(user);
        oauth.setProvider(this.providerKey);
        oauth.setProviderUserId(profileData.userIdentifier);
        oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(tokenResponse.accessToken));

        oauth.setCreatedAt(LocalDateTime.now());
        oauth.setUpdatedAt(LocalDateTime.now());
        oauth.setTokenMeta(buildTokenMeta(profileData, tokenResponse));

        return oauth;
    }

    private void updateOAuthIdentity(
            UserOAuthIdentity oauth,
            UserProfileData profileData,
            NotionTokenResponse tokenResponse) {

        oauth.setProviderUserId(profileData.userIdentifier);
        oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(tokenResponse.accessToken));

        oauth.setUpdatedAt(LocalDateTime.now());
        oauth.setTokenMeta(buildTokenMeta(profileData, tokenResponse));
    }

    private Map<String, Object> buildTokenMeta(UserProfileData profileData, NotionTokenResponse tokenResponse) {
        Map<String, Object> tokenMeta = new HashMap<>();
        tokenMeta.put("email", profileData.email);
        tokenMeta.put("name", profileData.name);
        tokenMeta.put("avatar_url", profileData.avatarUrl);
        tokenMeta.put("workspace_name", tokenResponse.workspaceName);
        tokenMeta.put("workspace_id", tokenResponse.workspaceId);
        tokenMeta.put("workspace_icon", tokenResponse.workspaceIcon);
        tokenMeta.put("bot_id", tokenResponse.botId);

        if (tokenResponse.duplicatedTemplateId != null && !tokenResponse.duplicatedTemplateId.isBlank()) {
            tokenMeta.put("duplicated_template_id", tokenResponse.duplicatedTemplateId);
        }

        return tokenMeta;
    }

    private NotionTokenResponse exchangeCodeForToken(String authorizationCode) {
        tokenExchangeCalls.increment();
        try {
            String tokenUrl = "https://api.notion.com/v1/oauth/token";

            String credentials = this.clientId + ":" + this.clientSecret;
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Basic " + encodedCredentials);
            headers.set("Notion-Version", "2022-06-28");

            Map<String, String> body = new HashMap<>();
            body.put("grant_type", "authorization_code");
            body.put("code", authorizationCode);
            body.put("redirect_uri", this.redirectBaseUrl + "/oauth-callback");

            HttpEntity<Map<String, String>> notionRequest = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> tokenResponse = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                notionRequest,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("Failed to exchange token with Notion");
            }

            Map<String, Object> responseBody = tokenResponse.getBody();

            String accessToken = (String) responseBody.get("access_token");
            if (accessToken == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("No access_token in Notion response");
            }

            String workspaceName = null;
            String workspaceId = null;
            String workspaceIcon = null;
            Object workspaceObj = responseBody.get("workspace_name");
            if (workspaceObj != null) {
                workspaceName = workspaceObj.toString();
            }

            Object workspaceIdObj = responseBody.get("workspace_id");
            if (workspaceIdObj != null) {
                workspaceId = workspaceIdObj.toString();
            }

            Object workspaceIconObj = responseBody.get("workspace_icon");
            if (workspaceIconObj != null) {
                workspaceIcon = workspaceIconObj.toString();
            }

            String botId = null;
            Object botIdObj = responseBody.get("bot_id");
            if (botIdObj != null) {
                botId = botIdObj.toString();
            }

            String duplicatedTemplateId = null;
            Object templateIdObj = responseBody.get("duplicated_template_id");
            if (templateIdObj != null) {
                duplicatedTemplateId = templateIdObj.toString();
            }

            UserProfileData ownerProfile = extractOwnerProfile(responseBody);

            return new NotionTokenResponse(
                accessToken,
                workspaceName,
                workspaceId,
                workspaceIcon,
                botId,
                duplicatedTemplateId,
                ownerProfile
            );
        } catch (Exception e) {
            tokenExchangeFailures.increment();
            log.error("Token exchange failed", e);
            throw new RuntimeException("Failed to exchange authorization code: " + e.getMessage(), e);
        }
    }

    private UserProfileData extractOwnerProfile(Map<String, Object> tokenResponseBody) {
        String userId = null;
        String email = null;
        String name = null;
        String avatarUrl = null;

        Object ownerObj = tokenResponseBody.get("owner");
        if (ownerObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> owner = (Map<String, Object>) ownerObj;

            Object typeObj = owner.get("type");
            if ("user".equals(typeObj)) {
                Object userObj = owner.get("user");
                if (userObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> user = (Map<String, Object>) userObj;

                    Object idObj = user.get("id");
                    if (idObj != null) {
                        userId = idObj.toString();
                    }

                    Object nameObj = user.get("name");
                    if (nameObj != null) {
                        name = nameObj.toString();
                    }

                    Object avatarObj = user.get("avatar_url");
                    if (avatarObj != null) {
                        avatarUrl = avatarObj.toString();
                    }

                    Object personObj = user.get("person");
                    if (personObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> person = (Map<String, Object>) personObj;
                        Object emailObj = person.get("email");
                        if (emailObj != null) {
                            email = emailObj.toString();
                        }
                    }
                }
            }
        }

        if (userId == null) {
            Object botIdObj = tokenResponseBody.get("bot_id");
            if (botIdObj != null) {
                userId = botIdObj.toString();
                log.warn("Could not extract user ID from owner, using bot_id as fallback: {}", userId);
            } else {
                throw new RuntimeException("Could not extract user identifier from Notion OAuth response");
            }
        }

        if (email == null || email.isBlank()) {
            log.warn("No email found in Notion OAuth response for user {}", userId);
            email = null;
        }

        if (name == null || name.isBlank()) {
            name = "Notion User";
            log.warn("No name found for Notion user {}, using default: {}", userId, name);
        }

        String emailLog;
        if (email != null) {
            emailLog = email;
        } else {
            emailLog = "null";
        }
        String avatarLog;
        if (avatarUrl != null) {
            avatarLog = "present";
        } else {
            avatarLog = "null";
        }

        log.info("Extracted Notion user profile - ID: {}, Name: {}, Email: {}, Avatar: {}",
                 userId, name, emailLog, avatarLog);

        return new UserProfileData(email, avatarUrl, userId, name);
    }

    private static class NotionTokenResponse {
        final String accessToken;
        final String workspaceName;
        final String workspaceId;
        final String workspaceIcon;
        final String botId;
        final String duplicatedTemplateId;
        final UserProfileData ownerProfile;

        NotionTokenResponse(
            String accessToken,
            String workspaceName,
            String workspaceId,
            String workspaceIcon,
            String botId,
            String duplicatedTemplateId,
            UserProfileData ownerProfile
        ) {
            this.accessToken = accessToken;
            this.workspaceName = workspaceName;
            this.workspaceId = workspaceId;
            this.workspaceIcon = workspaceIcon;
            this.botId = botId;
            this.duplicatedTemplateId = duplicatedTemplateId;
            this.ownerProfile = ownerProfile;
        }
    }

    private static class UserProfileData {
        final String email;
        final String avatarUrl;
        final String userIdentifier;
        final String name;

        UserProfileData(
            String email,
            String avatarUrl,
            String userIdentifier,
            String name
        ) {
            this.email = email;
            this.avatarUrl = avatarUrl;
            this.userIdentifier = userIdentifier;
            this.name = name;
        }
    }
}
