package area.server.AREA_Back.service;

import java.util.Optional;

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

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;

@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-id")
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.github", name = "client-secret")
@Service
public class OAuthGithubService extends OAuthService {

    private final RestTemplate restTemplate = new RestTemplate();

    public OAuthGithubService(
        @Value("${spring.security.oauth2.client.registration.github.client-id}") String githubClientId,
        @Value("${spring.security.oauth2.client.registration.github.client-secret}") String githubClientSecret,
        PasswordEncoder passwordEncoder,
        UserOAuthIdentityRepository userOAuthIdentityRepository,
        UserRepository userRepository,
        RedisTokenService redisTokenService,
        JwtService jwtService
    ) {
        super(
            "github",
            "GitHub",
            "https://images.icon-icons.com/2845/PNG/512/github_logo_icon_181401.png",
            "https://github.com/login/oauth/authorize?client_id=" + githubClientId + "&scope=user:email",
            githubClientId,
            githubClientSecret,
            jwtService
        );
        this.passwordEncoder = passwordEncoder;
        this.userOAuthIdentityRepository = userOAuthIdentityRepository;
        this.userRepository = userRepository;
        this.redisTokenService = redisTokenService;
    }

    @Override
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        //! 1: Exchange code for access token
        String tokenUrl = "https://github.com/login/oauth/access_token";


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", this.clientId);
        body.add("client_secret", this.clientSecret);
        body.add("code", request.getAuthorizationCode());

        HttpEntity<MultiValueMap<String, String>> githubRequest = new HttpEntity<>(body, headers);

        ResponseEntity<java.util.Map<String, Object>> tokenResponse = restTemplate.exchange(
            tokenUrl,
            HttpMethod.POST,
            githubRequest,
            new ParameterizedTypeReference<java.util.Map<String, Object>>(){}
        );

        if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
            throw new RuntimeException("Failed to exchange token with GitHub");
        }

        Object githubAccessTokenObj = tokenResponse.getBody().get("access_token");
        if (githubAccessTokenObj == null) {
            throw new RuntimeException("No access_token in GitHub response");
        }
        String githubAccessToken = githubAccessTokenObj.toString();

        //! 2: Fetch user profile
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(githubAccessToken);
        authHeaders.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> authRequest = new HttpEntity<>(authHeaders);

        ResponseEntity<java.util.Map<String, Object>> userResp = restTemplate.exchange(
            "https://api.github.com/user",
            HttpMethod.GET,
            authRequest,
            new ParameterizedTypeReference<java.util.Map<String, Object>>(){}
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
                    new ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>(){}
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
                } else {
                }
            } catch (Exception e) {
            }
        }

        //! 3: Validate email (now mandatory)
        if (email == null || email.isEmpty()) {
            throw new RuntimeException("GitHub account email is required for authentication");
        }

        String userIdentifier = idObj.toString();

        //! 4: If registered, update; else create user and OAuth identity
        Optional<UserOAuthIdentity> oauthOpt;
        try {
            oauthOpt = userOAuthIdentityRepository.findByProviderAndProviderUserId(this.providerKey, userIdentifier);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Database error during OAuth identity lookup: " + e.getMessage(), e);
        }

        UserOAuthIdentity oauth;

        if (oauthOpt.isPresent()) {
            oauth = oauthOpt.get();
            oauth.setAccessTokenEnc(passwordEncoder.encode(githubAccessToken));
            this.userOAuthIdentityRepository.save(oauth);

            User user = oauth.getUser();
            user.setLastLoginAt(java.time.LocalDateTime.now());
            user.setEmail(email);
            if (avatarUrl != null && !avatarUrl.isEmpty())
                user.setAvatarUrl(avatarUrl);
            this.userRepository.save(user);
        } else {
            Optional<User> userOpt = Optional.empty();
            if (email != null && !email.isEmpty()) {
                userOpt = userRepository.findByEmail(email);
            }

            oauth = new UserOAuthIdentity();

            if (userOpt.isPresent()) {
                oauth.setUser(userOpt.get());
            } else {
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setIsActive(true);
                newUser.setIsAdmin(false);
                newUser.setCreatedAt(java.time.LocalDateTime.now());
                if (avatarUrl != null && !avatarUrl.isEmpty())
                    newUser.setAvatarUrl(avatarUrl);
                User savedUser = this.userRepository.save(newUser);
                oauth.setUser(savedUser);
            }

            oauth.setProvider(this.providerKey);
            oauth.setProviderUserId(userIdentifier);
            oauth.setAccessTokenEnc(passwordEncoder.encode(githubAccessToken));
            oauth.setCreatedAt(java.time.LocalDateTime.now());
            oauth.setUpdatedAt(java.time.LocalDateTime.now());
            this.userOAuthIdentityRepository.save(oauth);
        }

        //! 5: Set cookies and return response
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
