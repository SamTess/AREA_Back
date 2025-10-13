package area.server.AREA_Back.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.extern.slf4j.Slf4j;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;

import jakarta.annotation.PostConstruct;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Slf4j
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.github.client-id")
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.github.client-secret")
@Service
public class OAuthGithubService extends OAuthService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String redirectBaseUrl;

    @Autowired
    private TokenEncryptionService tokenEncryptionService;

    @Autowired
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    private final MeterRegistry meterRegistry;

    private Counter oauthLoginSuccessCounter;
    private Counter oauthLoginFailureCounter;
    private Counter authenticateCalls;
    private Counter tokenExchangeCalls;
    private Counter tokenExchangeFailures;

    // CHECKSTYLE:OFF ParameterNumber
    public OAuthGithubService(
        @Value("${spring.security.oauth2.client.registration.github.client-id}") String githubClientId,
        @Value("${spring.security.oauth2.client.registration.github.client-secret}") String githubClientSecret,
        @Value("${OAUTH_REDIRECT_BASE_URL:http://localhost:3000}") String redirectBaseUrl,
        JwtService jwtService,
        MeterRegistry meterRegistry,
        RedisTokenService redisTokenService,
        PasswordEncoder passwordEncoder,
        AuthService authService
    ) {
        // CHECKSTYLE:OFF LineLength
        super(
            "github",
            "GitHub",
            "/oauth-icons/github.svg",
            "https://github.com/login/oauth/authorize?client_id=" + githubClientId
                + "&scope=user:email&redirect_uri=" + redirectBaseUrl + "/oauth-callback",
            githubClientId,
            githubClientSecret,
            jwtService
        );
        // CHECKSTYLE:ON LineLength
        this.redirectBaseUrl = redirectBaseUrl;
        this.meterRegistry = meterRegistry;
        this.authService = authService;
    }
    // CHECKSTYLE:ON ParameterNumber

    @PostConstruct
    private void initMetrics() {
        this.oauthLoginSuccessCounter = Counter.builder("oauth.github.login.success")
            .description("Number of successful GitHub OAuth logins")
            .register(meterRegistry);

        this.oauthLoginFailureCounter = Counter.builder("oauth.github.login.failure")
            .description("Number of failed GitHub OAuth logins")
            .register(meterRegistry);

        this.authenticateCalls = Counter.builder("oauth.github.authenticate.calls")
            .description("Total number of GitHub OAuth authenticate calls")
            .register(meterRegistry);

        this.tokenExchangeCalls = Counter.builder("oauth.github.token_exchange.calls")
            .description("Total number of GitHub token exchange calls")
            .register(meterRegistry);

        this.tokenExchangeFailures = Counter.builder("oauth.github.token_exchange.failures")
            .description("Total number of GitHub token exchange failures")
            .register(meterRegistry);
    }

    @Override
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        authenticateCalls.increment();
        try {
            log.info("Starting GitHub OAuth authentication");
            String githubAccessToken = exchangeCodeForToken(request.getAuthorizationCode());
            log.info("Successfully exchanged code for GitHub access token");

            UserProfileData profileData = fetchUserProfile(githubAccessToken);
            log.info("Fetched GitHub user profile");

            if (profileData.email == null || profileData.email.isEmpty()) {
                profileData.email = "github-" + profileData.userIdentifier + "@oauth.placeholder";
                log.warn("GitHub user {} has no email; using placeholder {}", profileData.userIdentifier, profileData.email);
            }

            log.info("Calling authService.oauthLogin for email: {}", profileData.email);
            AuthResponse authResponse = authService.oauthLogin(profileData.email, profileData.avatarUrl, response);
            log.info("AuthService.oauthLogin completed successfully for user: {}", authResponse.getUser().getId());

            User user = userRepository.findById(authResponse.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found after login"));

            Optional<UserOAuthIdentity> oauthOpt = userOAuthIdentityRepository.findByUserAndProvider(user, this.providerKey);
            UserOAuthIdentity oauth;
            if (oauthOpt.isPresent()) {
                oauth = oauthOpt.get();
                oauth.setProviderUserId(profileData.userIdentifier);
                oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(githubAccessToken));
                oauth.setUpdatedAt(java.time.LocalDateTime.now());
                log.info("Updated existing OAuth identity for user: {}", user.getId());
            } else {
                oauth = new UserOAuthIdentity();
                oauth.setUser(user);
                oauth.setProvider(this.providerKey);
                oauth.setProviderUserId(profileData.userIdentifier);
                oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(githubAccessToken));
                oauth.setCreatedAt(java.time.LocalDateTime.now());
                oauth.setUpdatedAt(java.time.LocalDateTime.now());
                log.info("Created new OAuth identity for user: {}", user.getId());
            }

            java.util.Map<String, Object> tokenMeta = new java.util.HashMap<>();
            tokenMeta.put("name", profileData.name);
            tokenMeta.put("login", profileData.login);
            tokenMeta.put("avatar_url", profileData.avatarUrl);
            oauth.setTokenMeta(tokenMeta);
            userOAuthIdentityRepository.save(oauth);
            log.info("Saved OAuth identity successfully");

            oauthLoginSuccessCounter.increment();
            log.info("GitHub OAuth authentication completed successfully for user: {}", user.getId());
            return authResponse;
        } catch (Exception e) {
            log.error("GitHub OAuth authentication failed", e);
            oauthLoginFailureCounter.increment();
            throw new RuntimeException("OAuth authentication failed: " + e.getMessage(), e);
        }
    }

    public UserOAuthIdentity linkToExistingUser(User existingUser, String authorizationCode) {
        String githubAccessToken = exchangeCodeForToken(authorizationCode);
        UserProfileData profileData = fetchUserProfile(githubAccessToken);

        if (profileData.email == null || profileData.email.isEmpty()) {
            throw new RuntimeException("GitHub account email is required for account linking");
        }

        Optional<UserOAuthIdentity> existingOAuth = userOAuthIdentityRepository
            .findByProviderAndProviderUserId(this.providerKey, profileData.userIdentifier);

        if (existingOAuth.isPresent() && !existingOAuth.get().getUser().getId().equals(existingUser.getId())) {
            throw new RuntimeException("This GitHub account is already linked to another user");
        }

        Optional<UserOAuthIdentity> userOAuthOpt = userOAuthIdentityRepository
            .findByUserAndProvider(existingUser, this.providerKey);

        UserOAuthIdentity oauth;
        if (userOAuthOpt.isPresent()) {
            oauth = userOAuthOpt.get();
            oauth.setProviderUserId(profileData.userIdentifier);
            oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(githubAccessToken));
            oauth.setUpdatedAt(java.time.LocalDateTime.now());
        } else {
            oauth = new UserOAuthIdentity();
            oauth.setUser(existingUser);
            oauth.setProvider(this.providerKey);
            oauth.setProviderUserId(profileData.userIdentifier);
            oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(githubAccessToken));
            oauth.setCreatedAt(java.time.LocalDateTime.now());
            oauth.setUpdatedAt(java.time.LocalDateTime.now());
        }

        java.util.Map<String, Object> tokenMeta = new java.util.HashMap<>();
        tokenMeta.put("name", profileData.name);
        tokenMeta.put("login", profileData.login);
        tokenMeta.put("avatar_url", profileData.avatarUrl);
        oauth.setTokenMeta(tokenMeta);

        try {
            oauth = this.userOAuthIdentityRepository.save(oauth);
            log.info("Successfully linked GitHub account {} to user {}",
                    profileData.userIdentifier, existingUser.getId());
            return oauth;
        } catch (Exception e) {
            log.error("Failed to link GitHub account to existing user", e);
            throw new RuntimeException("Failed to link GitHub account: " + e.getMessage(), e);
        }
    }

    private String exchangeCodeForToken(String authorizationCode) {
        tokenExchangeCalls.increment();
        try {
            String tokenUrl = "https://github.com/login/oauth/access_token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", this.clientId);
            body.add("client_secret", this.clientSecret);
            body.add("code", authorizationCode);
            body.add("redirect_uri", this.redirectBaseUrl + "/oauth-callback");

            HttpEntity<MultiValueMap<String, String>> githubRequest = new HttpEntity<>(body, headers);

            ResponseEntity<java.util.Map<String, Object>> tokenResponse = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                githubRequest,
                new ParameterizedTypeReference<java.util.Map<String, Object>>() { }
            );

            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("Failed to exchange token with GitHub");
            }

            Object githubAccessTokenObj = tokenResponse.getBody().get("access_token");
            if (githubAccessTokenObj == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("No access_token in GitHub response");
            }
            return githubAccessTokenObj.toString();
        } catch (Exception e) {
            tokenExchangeFailures.increment();
            throw e;
        }
    }

    private static class UserProfileData {
        String email;
        String avatarUrl;
        String userIdentifier;
        String name;
        String login;

        UserProfileData(String email, String avatarUrl, String userIdentifier, String name, String login) {
            this.email = email;
            this.avatarUrl = avatarUrl;
            this.userIdentifier = userIdentifier;
            this.name = name;
            this.login = login;
        }
    }

    private UserProfileData fetchUserProfile(String githubAccessToken) {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(githubAccessToken);
        authHeaders.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> authRequest = new HttpEntity<>(authHeaders);

        ResponseEntity<java.util.Map<String, Object>> userResp = restTemplate.exchange(
            "https://api.github.com/user",
            HttpMethod.GET,
            authRequest,
            new ParameterizedTypeReference<java.util.Map<String, Object>>() { }
        );

        if (!userResp.getStatusCode().is2xxSuccessful() || userResp.getBody() == null) {
            throw new RuntimeException("Failed to fetch GitHub user profile");
        }

        java.util.Map<String, Object> userBody = userResp.getBody();
        Object avatarUrlObj = userBody.get("avatar_url");
        String avatarUrl;
        if (avatarUrlObj != null) {
            avatarUrl = avatarUrlObj.toString();
        } else {
            avatarUrl = "";
        }
        Object nameObj = userBody.get("name");
        String name;
        if (nameObj != null) {
            name = nameObj.toString();
        } else {
            name = "";
        }
        Object loginObj = userBody.get("login");
        String login;
        if (loginObj != null) {
            login = loginObj.toString();
        } else {
            login = "";
        }

        Object idObj = userBody.get("id");
        if (idObj == null) {
            throw new RuntimeException("GitHub user id is missing in profile response");
        }

        String email = extractEmail(userBody, authRequest);
        return new UserProfileData(email, avatarUrl, idObj.toString(), name, login);
    }

    private String extractEmail(java.util.Map<String, Object> userBody, HttpEntity<Void> authRequest) {
        String email = null;
        Object emailObj = userBody.get("email");
        if (emailObj != null && !emailObj.toString().isEmpty()) {
            email = emailObj.toString();
        } else {
            try {
                ResponseEntity<java.util.List<java.util.Map<String, Object>>> emailsResp = restTemplate.exchange(
                    "https://api.github.com/user/emails",
                    HttpMethod.GET,
                    authRequest,
                    new ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>() { }
                );

                if (emailsResp.getStatusCode().is2xxSuccessful() && emailsResp.getBody() != null) {
                    for (java.util.Map<String, Object> m : emailsResp.getBody()) {
                        Object primary = m.get("primary");
                        Object verified = m.get("verified");
                        Object emailVal = m.get("email");
                        if (emailVal != null && Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified)) {
                            email = emailVal.toString();
                            break;
                        }
                        if (email == null && emailVal != null) {
                            email = emailVal.toString();
                        }
                    }
                }
            } catch (Exception e) {
                // Failed to fetch emails, continue without email verification
            }
        }
        return email;
    }
}
