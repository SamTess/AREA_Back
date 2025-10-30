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
import area.server.AREA_Back.service.Webhook.DiscordWelcomeService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.discord.client-id")
@ConditionalOnProperty(name = "spring.security.oauth2.client.registration.discord.client-secret")
@Service
public class OAuthDiscordService extends OAuthService {

    private static final int DEFAULT_TOKEN_EXPIRY_DAYS = 7;

    private final RestTemplate restTemplate;
    private final String redirectBaseUrl;
    private final TokenEncryptionService tokenEncryptionService;
    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;
    private final AuthService authService;
    private final DiscordWelcomeService discordWelcomeService;

    private Counter oauthLoginSuccessCounter;
    private Counter oauthLoginFailureCounter;
    private Counter authenticateCalls;
    private Counter tokenExchangeCalls;
    private Counter tokenExchangeFailures;

    @SuppressWarnings({"ParameterNumber", "checkstyle:ParameterNumber"})
    public OAuthDiscordService(
        @Value("${spring.security.oauth2.client.registration.discord.client-id}") final String discordClientId,
        @Value("${spring.security.oauth2.client.registration.discord.client-secret}")
        final String discordClientSecret,
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
        final DiscordWelcomeService discordWelcomeService,
        final RestTemplate restTemplate
    ) {
        super(
            "discord",
            "Discord",
            "https://img.icons8.com/color/48/discord-logo.png",
            "https://discord.com/api/oauth2/authorize?client_id=" + discordClientId
                + "&redirect_uri=" + redirectBaseUrl + "/oauth-callback"
                + "&response_type=code"
                + "&scope=bot%20identify%20guilds%20email%20messages.read"
                + "&permissions=8",
            discordClientId,
            discordClientSecret,
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
        this.discordWelcomeService = discordWelcomeService;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    private void initMetrics() {
        this.oauthLoginSuccessCounter = Counter.builder("oauth.discord.login.success")
            .description("Number of successful Discord OAuth logins")
            .register(meterRegistry);

        this.oauthLoginFailureCounter = Counter.builder("oauth.discord.login.failure")
            .description("Number of failed Discord OAuth logins")
            .register(meterRegistry);

        this.authenticateCalls = Counter.builder("oauth.discord.authenticate.calls")
            .description("Total number of Discord OAuth authenticate calls")
            .register(meterRegistry);

        this.tokenExchangeCalls = Counter.builder("oauth.discord.token_exchange.calls")
            .description("Total number of Discord token exchange calls")
            .register(meterRegistry);

        this.tokenExchangeFailures = Counter.builder("oauth.discord.token_exchange.failures")
            .description("Total number of Discord token exchange failures")
            .register(meterRegistry);
    }

    @Override
    public AuthResponse authenticate(OAuthLoginRequest request, HttpServletResponse response) {
        authenticateCalls.increment();
        try {
            log.info("Starting Discord OAuth authentication");
            DiscordTokenResponse tokenResponse = exchangeCodeForToken(request.getAuthorizationCode());
            log.info("Successfully exchanged code for Discord access token");

            UserProfileData profileData = fetchUserProfile(tokenResponse.accessToken);
            log.info("Fetched Discord user profile: {}", profileData.userIdentifier);

            if (profileData.email == null || profileData.email.isEmpty()) {
                oauthLoginFailureCounter.increment();
                throw new RuntimeException("Discord account email is required for authentication");
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

            try {
                discordWelcomeService.sendWelcomeMessagesToNewGuilds(tokenResponse.accessToken);
            } catch (Exception e) {
                log.warn("Failed to send Discord welcome messages, but OAuth succeeded: {}", e.getMessage());
            }

            oauthLoginSuccessCounter.increment();
            log.info("Discord OAuth authentication completed successfully for user: {}", user.getId());
            return authResponse;
        } catch (Exception e) {
            log.error("Discord OAuth authentication failed", e);
            oauthLoginFailureCounter.increment();
            throw new RuntimeException("OAuth authentication failed: " + e.getMessage(), e);
        }
    }

    public UserOAuthIdentity linkToExistingUser(User existingUser, String authorizationCode) {
        DiscordTokenResponse tokenResponse = exchangeCodeForToken(authorizationCode);
        UserProfileData profileData = fetchUserProfile(tokenResponse.accessToken);

        if (profileData.email == null || profileData.email.isEmpty()) {
            throw new RuntimeException("Discord account email is required for account linking");
        }

        Optional<UserOAuthIdentity> existingOAuth = userOAuthIdentityRepository
            .findByProviderAndProviderUserId(this.providerKey, profileData.userIdentifier);

        if (existingOAuth.isPresent() && !existingOAuth.get().getUser().getId().equals(existingUser.getId())) {
            throw new RuntimeException("This Discord account is already linked to another user");
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
        tokenMeta.put("username", profileData.username);
        tokenMeta.put("discriminator", profileData.discriminator);
        tokenMeta.put("global_name", profileData.globalName);
        tokenMeta.put("avatar_url", profileData.avatarUrl);
        oauth.setTokenMeta(tokenMeta);

        try {
            oauth = userOAuthIdentityRepository.save(oauth);
            log.info("Successfully linked Discord account {} to user {}",
                    profileData.userIdentifier, existingUser.getId());

            try {
                discordWelcomeService.sendWelcomeMessagesToNewGuilds(tokenResponse.accessToken);
            } catch (Exception e) {
                log.warn("Failed to send Discord welcome messages, but account linking succeeded: {}", e.getMessage());
            }

            return oauth;
        } catch (Exception e) {
            log.error("Failed to link Discord account to existing user", e);
            throw new RuntimeException("Failed to link Discord account: " + e.getMessage(), e);
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
        tokenMeta.put("username", profileData.username);
        tokenMeta.put("discriminator", profileData.discriminator);
        tokenMeta.put("global_name", profileData.globalName);
        tokenMeta.put("avatar_url", profileData.avatarUrl);
        oauth.setTokenMeta(tokenMeta);

        return oauth;
    }

    private LocalDateTime calculateExpirationTime(Integer expiresIn) {
        if (expiresIn == null || expiresIn <= 0) {
            return LocalDateTime.now().plusDays(DEFAULT_TOKEN_EXPIRY_DAYS);
        }
        return LocalDateTime.now().plusSeconds(expiresIn);
    }

    private DiscordTokenResponse exchangeCodeForToken(String authorizationCode) {
        tokenExchangeCalls.increment();
        try {
            String tokenUrl = "https://discord.com/api/oauth2/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", this.clientId);
            body.add("client_secret", this.clientSecret);
            body.add("grant_type", "authorization_code");
            body.add("code", authorizationCode);
            body.add("redirect_uri", this.redirectBaseUrl + "/oauth-callback");

            HttpEntity<MultiValueMap<String, String>> discordRequest = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> tokenResponse = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                discordRequest,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("Failed to exchange token with Discord");
            }

            Map<String, Object> body2 = tokenResponse.getBody();
            Object accessTokenObj = body2.get("access_token");
            if (accessTokenObj == null) {
                tokenExchangeFailures.increment();
                throw new RuntimeException("No access_token in Discord response");
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

            return new DiscordTokenResponse(accessTokenObj.toString(), refreshToken, expiresIn);
        } catch (Exception e) {
            tokenExchangeFailures.increment();
            log.error("Token exchange failed", e);
            throw new RuntimeException("Failed to exchange authorization code: " + e.getMessage(), e);
        }
    }

    private UserProfileData fetchUserProfile(String discordAccessToken) {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(discordAccessToken);
        authHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> authRequest = new HttpEntity<>(authHeaders);

        ResponseEntity<Map<String, Object>> userResp = restTemplate.exchange(
            "https://discord.com/api/users/@me",
            HttpMethod.GET,
            authRequest,
            new ParameterizedTypeReference<Map<String, Object>>() { }
        );

        if (!userResp.getStatusCode().is2xxSuccessful() || userResp.getBody() == null) {
            throw new RuntimeException("Failed to fetch Discord user profile");
        }

        Map<String, Object> userBody = userResp.getBody();

        Object idObj = userBody.get("id");
        if (idObj == null) {
            throw new RuntimeException("Discord user id is missing in profile response");
        }

        Object emailObj = userBody.get("email");
        String email = null;
        if (emailObj != null) {
            email = emailObj.toString();
        }

        Object usernameObj = userBody.get("username");
        String username = "";
        if (usernameObj != null) {
            username = usernameObj.toString();
        }

        Object discriminatorObj = userBody.get("discriminator");
        String discriminator = "0";
        if (discriminatorObj != null) {
            discriminator = discriminatorObj.toString();
        }

        Object globalNameObj = userBody.get("global_name");
        String globalName = "";
        if (globalNameObj != null) {
            globalName = globalNameObj.toString();
        }

        Object avatarObj = userBody.get("avatar");
        String avatarUrl = "";
        if (avatarObj != null && !avatarObj.toString().isEmpty()) {
            avatarUrl = "https://cdn.discordapp.com/avatars/" + idObj.toString() + "/"
                + avatarObj.toString() + ".png";
        }

        return new UserProfileData(email, avatarUrl, idObj.toString(), username, discriminator, globalName);
    }

    private static class DiscordTokenResponse {
        final String accessToken;
        final String refreshToken;
        final Integer expiresIn;

        DiscordTokenResponse(String accessToken, String refreshToken, Integer expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }
    }

    private static class UserProfileData {
        final String email;
        final String avatarUrl;
        final String userIdentifier;
        final String username;
        final String discriminator;
        final String globalName;

        UserProfileData(String email, String avatarUrl, String userIdentifier, String username,
                        String discriminator, String globalName) {
            this.email = email;
            this.avatarUrl = avatarUrl;
            this.userIdentifier = userIdentifier;
            this.username = username;
            this.discriminator = discriminator;
            this.globalName = globalName;
        }
    }
}
