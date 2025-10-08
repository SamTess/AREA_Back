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
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;

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

    public OAuthGithubService(
        @Value("${spring.security.oauth2.client.registration.github.client-id}") String githubClientId,
        @Value("${spring.security.oauth2.client.registration.github.client-secret}") String githubClientSecret,
        @Value("${OAUTH_REDIRECT_BASE_URL:http://localhost:3000}") String redirectBaseUrl,
        JwtService jwtService,
        RedisTokenService redisTokenService,
        PasswordEncoder passwordEncoder
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
        this.redisTokenService = redisTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        String githubAccessToken = exchangeCodeForToken(request.getAuthorizationCode());
        UserProfileData profileData = fetchUserProfile(githubAccessToken);
        if (profileData.email == null || profileData.email.isEmpty()) {
            throw new RuntimeException("GitHub account email is required for authentication");
        }
        UserOAuthIdentity oauth = handleUserAuthentication(profileData, githubAccessToken);
        return generateAuthResponse(oauth, response);
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
            log.info("Successfully linked GitHub account {} to user {}", profileData.userIdentifier, existingUser.getId());
            return oauth;
        } catch (Exception e) {
            log.error("Failed to link GitHub account to existing user", e);
            throw new RuntimeException("Failed to link GitHub account: " + e.getMessage(), e);
        }
    }

    private String exchangeCodeForToken(String authorizationCode) {
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
            throw new RuntimeException("Failed to exchange token with GitHub");
        }

        Object githubAccessTokenObj = tokenResponse.getBody().get("access_token");
        if (githubAccessTokenObj == null) {
            throw new RuntimeException("No access_token in GitHub response");
        }
        return githubAccessTokenObj.toString();
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
        String avatarUrl = userBody.getOrDefault("avatar_url", "").toString();
        String name = userBody.getOrDefault("name", "").toString();
        String login = userBody.getOrDefault("login", "").toString();

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
