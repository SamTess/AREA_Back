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

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;

import jakarta.annotation.PostConstruct;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

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

    private final MeterRegistry meterRegistry;

    private Counter oauthLoginSuccessCounter;
    private Counter oauthLoginFailureCounter;
    private Counter authenticateCalls;
    private Counter tokenExchangeCalls;
    private Counter tokenExchangeFailures;

    public OAuthGithubService(
        @Value("${spring.security.oauth2.client.registration.github.client-id}") String githubClientId,
        @Value("${spring.security.oauth2.client.registration.github.client-secret}") String githubClientSecret,
        @Value("${OAUTH_REDIRECT_BASE_URL:http://localhost:3000}") String redirectBaseUrl,
        JwtService jwtService,
        MeterRegistry meterRegistry
    ) {
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
        this.redirectBaseUrl = redirectBaseUrl;
        this.meterRegistry = meterRegistry;
    }

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
            String githubAccessToken = exchangeCodeForToken(request.getAuthorizationCode());
            UserProfileData profileData = fetchUserProfile(githubAccessToken);
            if (profileData.email == null || profileData.email.isEmpty()) {
                oauthLoginFailureCounter.increment();
                throw new RuntimeException("GitHub account email is required for authentication");
            }
            UserOAuthIdentity oauth = handleUserAuthentication(profileData, githubAccessToken);
            oauthLoginSuccessCounter.increment();
            return generateAuthResponse(oauth, response);
        } catch (Exception e) {
            oauthLoginFailureCounter.increment();
            throw e;
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

        UserProfileData(String email, String avatarUrl, String userIdentifier) {
            this.email = email;
            this.avatarUrl = avatarUrl;
            this.userIdentifier = userIdentifier;
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
        String avatarUrl = userBody.getOrDefault("avatar_url", "").toString();

        Object idObj = userBody.get("id");
        if (idObj == null) {
            throw new RuntimeException("GitHub user id is missing in profile response");
        }

        String email = extractEmail(userBody, authRequest);
        return new UserProfileData(email, avatarUrl, idObj.toString());
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

    private UserOAuthIdentity handleUserAuthentication(UserProfileData profileData, String githubAccessToken) {
        Optional<User> userOpt = Optional.empty();
        if (profileData.email != null && !profileData.email.isEmpty()) {
            userOpt = userRepository.findByEmail(profileData.email);
        }

        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            user.setLastLoginAt(java.time.LocalDateTime.now());
            user.setEmail(profileData.email);
            if (profileData.avatarUrl != null && !profileData.avatarUrl.isEmpty()) {
                user.setAvatarUrl(profileData.avatarUrl);
            }
            this.userRepository.save(user);
        } else {
            user = new User();
            user.setEmail(profileData.email);
            user.setIsActive(true);
            user.setIsAdmin(false);
            user.setCreatedAt(java.time.LocalDateTime.now());
            if (profileData.avatarUrl != null && !profileData.avatarUrl.isEmpty()) {
                user.setAvatarUrl(profileData.avatarUrl);
            }
            user = this.userRepository.save(user);
        }

        Optional<UserOAuthIdentity> oauthOpt = userOAuthIdentityRepository
            .findByUserAndProvider(user, this.providerKey);

        UserOAuthIdentity oauth;
        if (oauthOpt.isPresent()) {
            oauth = oauthOpt.get();
            oauth.setProviderUserId(profileData.userIdentifier);
            oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(githubAccessToken));
            oauth.setUpdatedAt(java.time.LocalDateTime.now());
        } else {
            oauth = new UserOAuthIdentity();
            oauth.setUser(user);
            oauth.setProvider(this.providerKey);
            oauth.setProviderUserId(profileData.userIdentifier);
            oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(githubAccessToken));
            oauth.setCreatedAt(java.time.LocalDateTime.now());
            oauth.setUpdatedAt(java.time.LocalDateTime.now());
        }

        try {
            oauth = this.userOAuthIdentityRepository.save(oauth);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                oauthOpt = userOAuthIdentityRepository.findByUserAndProvider(user, this.providerKey);
                if (oauthOpt.isPresent()) {
                    oauth = oauthOpt.get();
                    oauth.setProviderUserId(profileData.userIdentifier);
                    oauth.setAccessTokenEnc(tokenEncryptionService.encryptToken(githubAccessToken));
                    oauth.setUpdatedAt(java.time.LocalDateTime.now());
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
            e.printStackTrace();
            throw new RuntimeException("JWT token generation failed: " + e.getMessage(), e);
        }

        try {
            redisTokenService.storeAccessToken(accessToken, user.getId());
            redisTokenService.storeRefreshToken(user.getId(), refreshToken);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Redis token storage failed: " + e.getMessage(), e);
        }

        try {
            setTokenCookies(response, accessToken, refreshToken);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Cookie setting failed: " + e.getMessage(), e);
        }

        return new AuthResponse(
            "Login successful",
            userResponse
        );
    }
}
