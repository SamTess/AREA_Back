package area.server.AREA_Back.service;

import area.server.AREA_Back.config.JwtCookieProperties;
import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.AuthService;
import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.Auth.OAuthSlackService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthSlackServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletResponse httpServletResponse;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthService authService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JwtCookieProperties jwtCookieProperties;

    private SimpleMeterRegistry meterRegistry;
    private OAuthSlackService oauthSlackService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        lenient().when(jwtCookieProperties.getAccessTokenExpiry()).thenReturn(900);
        lenient().when(jwtCookieProperties.getRefreshTokenExpiry()).thenReturn(604800);
        lenient().when(jwtCookieProperties.isSecure()).thenReturn(false);
        lenient().when(jwtCookieProperties.getSameSite()).thenReturn("Strict");
        lenient().when(jwtCookieProperties.getDomain()).thenReturn(null);

        oauthSlackService = new OAuthSlackService(
            "test-slack-client-id",
            "test-slack-client-secret",
            "http://localhost:3000",
            jwtService,
            jwtCookieProperties,
            meterRegistry,
            redisTokenService,
            passwordEncoder,
            tokenEncryptionService,
            userOAuthIdentityRepository,
            userRepository,
            authService,
            restTemplate
        );

        try {
            var initMetricsMethod = OAuthSlackService.class.getDeclaredMethod("initMetrics");
            initMetricsMethod.setAccessible(true);
            initMetricsMethod.invoke(oauthSlackService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(oauthSlackService);
        assertNotNull(meterRegistry);
        assertEquals("slack", oauthSlackService.getProviderKey());
        assertEquals("Slack", oauthSlackService.getProviderLabel());
    }

    @Test
    void testGetProviderKey() {
        assertEquals("slack", oauthSlackService.getProviderKey());
    }

    @Test
    void testGetProviderLabel() {
        assertEquals("Slack", oauthSlackService.getProviderLabel());
    }

    @Test
    void testGetClientId() {
        assertEquals("test-slack-client-id", oauthSlackService.getClientId());
    }

    // Test exchangeCodeForToken - Success case
    @Test
    void testExchangeCodeForTokenSuccess() throws Exception {
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("ok", true);
        
        Map<String, Object> authedUser = new HashMap<>();
        authedUser.put("access_token", "xoxp-test-token");
        authedUser.put("refresh_token", "xoxe-1-test-refresh");
        authedUser.put("expires_in", 43200);
        tokenResponseBody.put("authed_user", authedUser);
        
        Map<String, Object> team = new HashMap<>();
        team.put("id", "T12345");
        team.put("name", "Test Team");
        tokenResponseBody.put("team", team);
        tokenResponseBody.put("access_token", "xoxb-bot-token");

        ResponseEntity<Map<String, Object>> mockResponse = 
            new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        Method method = OAuthSlackService.class.getDeclaredMethod("exchangeCodeForToken", String.class);
        method.setAccessible(true);
        Object result = method.invoke(oauthSlackService, "test-auth-code");

        assertNotNull(result);
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    // Test exchangeCodeForToken - API returns ok=false
    @Test
    void testExchangeCodeForTokenApiError() throws Exception {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("ok", false);
        errorResponse.put("error", "invalid_code");

        ResponseEntity<Map<String, Object>> mockResponse = 
            new ResponseEntity<>(errorResponse, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        Method method = OAuthSlackService.class.getDeclaredMethod("exchangeCodeForToken", String.class);
        method.setAccessible(true);

        Exception exception = assertThrows(Exception.class, () -> {
            method.invoke(oauthSlackService, "invalid-code");
        });

        assertTrue(exception.getCause().getMessage().contains("Slack API returned error"));
    }

    // Test exchangeCodeForToken - No access token
    @Test
    void testExchangeCodeForTokenNoAccessToken() throws Exception {
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("ok", true);
        tokenResponseBody.put("authed_user", new HashMap<>());

        ResponseEntity<Map<String, Object>> mockResponse = 
            new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        Method method = OAuthSlackService.class.getDeclaredMethod("exchangeCodeForToken", String.class);
        method.setAccessible(true);

        Exception exception = assertThrows(Exception.class, () -> {
            method.invoke(oauthSlackService, "test-code");
        });

        assertTrue(exception.getCause().getMessage().contains("No access_token"));
    }

    // Test exchangeCodeForToken - Network error
    @Test
    void testExchangeCodeForTokenNetworkError() throws Exception {
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("Network error"));

        Method method = OAuthSlackService.class.getDeclaredMethod("exchangeCodeForToken", String.class);
        method.setAccessible(true);

        Exception exception = assertThrows(Exception.class, () -> {
            method.invoke(oauthSlackService, "test-code");
        });

        assertTrue(exception.getCause().getMessage().contains("Failed to exchange authorization code"));
    }

    // Test fetchUserProfile - Success
    @Test
    void testFetchUserProfileSuccess() throws Exception {
        Map<String, Object> identityResponse = new HashMap<>();
        identityResponse.put("ok", true);
        
        Map<String, Object> user = new HashMap<>();
        user.put("id", "U12345");
        user.put("email", "test@slack.com");
        user.put("name", "testuser");
        
        Map<String, Object> profile = new HashMap<>();
        profile.put("real_name", "Test User");
        profile.put("image_512", "https://example.com/avatar.jpg");
        user.put("profile", profile);
        
        identityResponse.put("user", user);

        ResponseEntity<Map<String, Object>> mockResponse = 
            new ResponseEntity<>(identityResponse, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        Method method = OAuthSlackService.class.getDeclaredMethod("fetchUserProfile", String.class);
        method.setAccessible(true);
        Object result = method.invoke(oauthSlackService, "xoxp-test-token");

        assertNotNull(result);
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    // Test fetchUserProfile - API error
    @Test
    void testFetchUserProfileApiError() throws Exception {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("ok", false);
        errorResponse.put("error", "invalid_auth");

        ResponseEntity<Map<String, Object>> mockResponse = 
            new ResponseEntity<>(errorResponse, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        Method method = OAuthSlackService.class.getDeclaredMethod("fetchUserProfile", String.class);
        method.setAccessible(true);

        Exception exception = assertThrows(Exception.class, () -> {
            method.invoke(oauthSlackService, "invalid-token");
        });

        assertTrue(exception.getCause().getMessage().contains("Slack identity API returned error"));
    }

    // Test fetchUserProfile - Missing user ID
    @Test
    void testFetchUserProfileMissingUserId() throws Exception {
        Map<String, Object> identityResponse = new HashMap<>();
        identityResponse.put("ok", true);
        identityResponse.put("user", new HashMap<>());

        ResponseEntity<Map<String, Object>> mockResponse = 
            new ResponseEntity<>(identityResponse, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        Method method = OAuthSlackService.class.getDeclaredMethod("fetchUserProfile", String.class);
        method.setAccessible(true);

        Exception exception = assertThrows(Exception.class, () -> {
            method.invoke(oauthSlackService, "test-token");
        });

        assertTrue(exception.getCause().getMessage().contains("Slack user id is missing"));
    }

    // Test authenticate - Success
    @Test
    void testAuthenticateSuccess() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("ok", true);
        Map<String, Object> authedUser = new HashMap<>();
        authedUser.put("access_token", "xoxp-test-token");
        tokenResponseBody.put("authed_user", authedUser);
        Map<String, Object> team = new HashMap<>();
        team.put("id", "T12345");
        team.put("name", "Test Team");
        tokenResponseBody.put("team", team);

        ResponseEntity<Map<String, Object>> tokenResponse = 
            new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        // Mock user profile fetch
        Map<String, Object> identityResponse = new HashMap<>();
        identityResponse.put("ok", true);
        Map<String, Object> user = new HashMap<>();
        user.put("id", "U12345");
        user.put("email", "test@slack.com");
        user.put("name", "testuser");
        Map<String, Object> profile = new HashMap<>();
        profile.put("real_name", "Test User");
        profile.put("image_512", "https://example.com/avatar.jpg");
        user.put("profile", profile);
        identityResponse.put("user", user);

        ResponseEntity<Map<String, Object>> identityResp = 
            new ResponseEntity<>(identityResponse, HttpStatus.OK);

        when(restTemplate.exchange(
            contains("oauth.v2.access"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        when(restTemplate.exchange(
            contains("users.identity"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(identityResp);

        User mockUser = new User();
        UUID userId = UUID.randomUUID();
        mockUser.setId(userId);
        mockUser.setEmail("test@slack.com");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("test@slack.com");

        AuthResponse mockAuthResponse = new AuthResponse();
        mockAuthResponse.setUser(userResponse);

        when(authService.oauthLogin(anyString(), anyString(), any(HttpServletResponse.class)))
            .thenReturn(mockAuthResponse);
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(mockUser));
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse result = oauthSlackService.authenticate(request, httpServletResponse);

        assertNotNull(result);
        verify(authService, times(1)).oauthLogin(anyString(), anyString(), any(HttpServletResponse.class));
        verify(userOAuthIdentityRepository, times(1)).save(any(UserOAuthIdentity.class));
    }

    // Test authenticate - Missing email
    @Test
    void testAuthenticateMissingEmail() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("ok", true);
        Map<String, Object> authedUser = new HashMap<>();
        authedUser.put("access_token", "xoxp-test-token");
        tokenResponseBody.put("authed_user", authedUser);

        ResponseEntity<Map<String, Object>> tokenResponse = 
            new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> identityResponse = new HashMap<>();
        identityResponse.put("ok", true);
        Map<String, Object> user = new HashMap<>();
        user.put("id", "U12345");
        // No email
        identityResponse.put("user", user);

        ResponseEntity<Map<String, Object>> identityResp = 
            new ResponseEntity<>(identityResponse, HttpStatus.OK);

        when(restTemplate.exchange(
            contains("oauth.v2.access"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        when(restTemplate.exchange(
            contains("users.identity"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(identityResp);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            oauthSlackService.authenticate(request, httpServletResponse);
        });

        assertTrue(exception.getMessage().contains("email is required"));
    }

    // Test linkToExistingUser - Success (new link)
    @Test
    void testLinkToExistingUserSuccess() throws Exception {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("existing@example.com");

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("ok", true);
        Map<String, Object> authedUser = new HashMap<>();
        authedUser.put("access_token", "xoxp-test-token");
        tokenResponseBody.put("authed_user", authedUser);
        Map<String, Object> team = new HashMap<>();
        team.put("id", "T12345");
        tokenResponseBody.put("team", team);

        ResponseEntity<Map<String, Object>> tokenResponse = 
            new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> identityResponse = new HashMap<>();
        identityResponse.put("ok", true);
        Map<String, Object> user = new HashMap<>();
        user.put("id", "U12345");
        user.put("email", "slack@example.com");
        identityResponse.put("user", user);

        ResponseEntity<Map<String, Object>> identityResp = 
            new ResponseEntity<>(identityResponse, HttpStatus.OK);

        when(restTemplate.exchange(
            contains("oauth.v2.access"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        when(restTemplate.exchange(
            contains("users.identity"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(identityResp);

        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UserOAuthIdentity result = oauthSlackService.linkToExistingUser(existingUser, "valid-code");

        assertNotNull(result);
        verify(userOAuthIdentityRepository, times(1)).save(any(UserOAuthIdentity.class));
    }

    // Test linkToExistingUser - Already linked to another user
    @Test
    void testLinkToExistingUserAlreadyLinked() throws Exception {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("ok", true);
        Map<String, Object> authedUser = new HashMap<>();
        authedUser.put("access_token", "xoxp-test-token");
        tokenResponseBody.put("authed_user", authedUser);

        ResponseEntity<Map<String, Object>> tokenResponse = 
            new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> identityResponse = new HashMap<>();
        identityResponse.put("ok", true);
        Map<String, Object> user = new HashMap<>();
        user.put("id", "U12345");
        user.put("email", "slack@example.com");
        identityResponse.put("user", user);

        ResponseEntity<Map<String, Object>> identityResp = 
            new ResponseEntity<>(identityResponse, HttpStatus.OK);

        when(restTemplate.exchange(
            contains("oauth.v2.access"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        when(restTemplate.exchange(
            contains("users.identity"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(identityResp);

        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(otherUser);

        when(userOAuthIdentityRepository.findByProviderAndProviderUserId(anyString(), anyString()))
            .thenReturn(Optional.of(existingOAuth));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            oauthSlackService.linkToExistingUser(existingUser, "valid-code");
        });

        assertTrue(exception.getMessage().contains("already linked to another user"));
    }

    // Test linkToExistingUser - Update existing link
    @Test
    void testLinkToExistingUserUpdateExisting() throws Exception {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("ok", true);
        Map<String, Object> authedUser = new HashMap<>();
        authedUser.put("access_token", "xoxp-new-token");
        tokenResponseBody.put("authed_user", authedUser);

        ResponseEntity<Map<String, Object>> tokenResponse = 
            new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> identityResponse = new HashMap<>();
        identityResponse.put("ok", true);
        Map<String, Object> user = new HashMap<>();
        user.put("id", "U12345");
        user.put("email", "slack@example.com");
        identityResponse.put("user", user);

        ResponseEntity<Map<String, Object>> identityResp = 
            new ResponseEntity<>(identityResponse, HttpStatus.OK);

        when(restTemplate.exchange(
            contains("oauth.v2.access"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        when(restTemplate.exchange(
            contains("users.identity"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(identityResp);

        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(existingUser);
        existingOAuth.setProvider("slack");

        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), anyString()))
            .thenReturn(Optional.of(existingOAuth));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UserOAuthIdentity result = oauthSlackService.linkToExistingUser(existingUser, "valid-code");

        assertNotNull(result);
        verify(userOAuthIdentityRepository, times(1)).save(any(UserOAuthIdentity.class));
    }

    // Test buildTokenMeta
    @Test
    void testBuildTokenMeta() throws Exception {
        Class<?> userProfileDataClass = Class.forName(
            "area.server.AREA_Back.service.Auth.OAuthSlackService$UserProfileData"
        );
        Class<?> slackTokenResponseClass = Class.forName(
            "area.server.AREA_Back.service.Auth.OAuthSlackService$SlackTokenResponse"
        );

        var profileDataConstructor = userProfileDataClass
            .getDeclaredConstructor(String.class, String.class, String.class, String.class, String.class);
        profileDataConstructor.setAccessible(true);
        Object profileData = profileDataConstructor
            .newInstance("test@slack.com", "https://avatar.url", "U12345", "testuser", "Test User");

        var tokenResponseConstructor = slackTokenResponseClass
            .getDeclaredConstructor(String.class, String.class, Integer.class, String.class, String.class, String.class);
        tokenResponseConstructor.setAccessible(true);
        Object tokenResponse = tokenResponseConstructor
            .newInstance("xoxp-token", "xoxe-refresh", 43200, "T12345", "Test Team", "xoxb-bot-token");

        when(tokenEncryptionService.encryptToken("xoxb-bot-token")).thenReturn("encrypted-bot-token");

        Method method = OAuthSlackService.class.getDeclaredMethod(
            "buildTokenMeta",
            userProfileDataClass,
            slackTokenResponseClass
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(
            oauthSlackService,
            profileData,
            tokenResponse
        );

        assertNotNull(result);
        assertEquals("test@slack.com", result.get("email"));
        assertEquals("testuser", result.get("display_name"));
        assertEquals("Test User", result.get("real_name"));
        assertEquals("https://avatar.url", result.get("avatar_url"));
        assertEquals("T12345", result.get("team_id"));
        assertEquals("Test Team", result.get("team_name"));
        assertEquals("encrypted-bot-token", result.get("bot_access_token_enc"));
    }

    // Test createOAuthIdentity
    @Test
    void testCreateOAuthIdentity() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());

        Class<?> userProfileDataClass = Class.forName(
            "area.server.AREA_Back.service.Auth.OAuthSlackService$UserProfileData"
        );
        Class<?> slackTokenResponseClass = Class.forName(
            "area.server.AREA_Back.service.Auth.OAuthSlackService$SlackTokenResponse"
        );

        var profileDataConstructor = userProfileDataClass
            .getDeclaredConstructor(String.class, String.class, String.class, String.class, String.class);
        profileDataConstructor.setAccessible(true);
        Object profileData = profileDataConstructor
            .newInstance("test@slack.com", "https://avatar.url", "U12345", "testuser", "Test User");

        var tokenResponseConstructor = slackTokenResponseClass
            .getDeclaredConstructor(String.class, String.class, Integer.class, String.class, String.class, String.class);
        tokenResponseConstructor.setAccessible(true);
        Object tokenResponse = tokenResponseConstructor
            .newInstance("xoxp-token", "xoxe-refresh", 43200, "T12345", "Test Team", null);

        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        Method method = OAuthSlackService.class.getDeclaredMethod(
            "createOAuthIdentity",
            User.class,
            userProfileDataClass,
            slackTokenResponseClass
        );
        method.setAccessible(true);

        UserOAuthIdentity result = (UserOAuthIdentity) method.invoke(
            oauthSlackService,
            user,
            profileData,
            tokenResponse
        );

        assertNotNull(result);
        assertEquals(user, result.getUser());
        assertEquals("slack", result.getProvider());
        assertEquals("U12345", result.getProviderUserId());
        assertNotNull(result.getAccessTokenEnc());
        assertNotNull(result.getExpiresAt());
    }

    // Test updateOAuthIdentity
    @Test
    void testUpdateOAuthIdentity() throws Exception {
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("slack");
        oauth.setProviderUserId("OLD_ID");

        Class<?> userProfileDataClass = Class.forName(
            "area.server.AREA_Back.service.Auth.OAuthSlackService$UserProfileData"
        );
        Class<?> slackTokenResponseClass = Class.forName(
            "area.server.AREA_Back.service.Auth.OAuthSlackService$SlackTokenResponse"
        );

        var profileDataConstructor = userProfileDataClass
            .getDeclaredConstructor(String.class, String.class, String.class, String.class, String.class);
        profileDataConstructor.setAccessible(true);
        Object profileData = profileDataConstructor
            .newInstance("new@slack.com", "https://new-avatar.url", "U67890", "newuser", "New User");

        var tokenResponseConstructor = slackTokenResponseClass
            .getDeclaredConstructor(String.class, String.class, Integer.class, String.class, String.class, String.class);
        tokenResponseConstructor.setAccessible(true);
        Object tokenResponse = tokenResponseConstructor
            .newInstance("xoxp-new-token", "xoxe-new-refresh", 43200, "T67890", "New Team", null);

        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("new-encrypted-token");

        Method method = OAuthSlackService.class.getDeclaredMethod(
            "updateOAuthIdentity",
            UserOAuthIdentity.class,
            userProfileDataClass,
            slackTokenResponseClass
        );
        method.setAccessible(true);

        method.invoke(oauthSlackService, oauth, profileData, tokenResponse);

        assertEquals("U67890", oauth.getProviderUserId());
        assertEquals("new-encrypted-token", oauth.getAccessTokenEnc());
        assertNotNull(oauth.getUpdatedAt());
    }

    // Test handleUserAuthentication - New OAuth
    @Test
    void testHandleUserAuthenticationNewOAuth() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());

        Class<?> userProfileDataClass = Class.forName(
            "area.server.AREA_Back.service.Auth.OAuthSlackService$UserProfileData"
        );
        Class<?> slackTokenResponseClass = Class.forName(
            "area.server.AREA_Back.service.Auth.OAuthSlackService$SlackTokenResponse"
        );

        var profileDataConstructor = userProfileDataClass
            .getDeclaredConstructor(String.class, String.class, String.class, String.class, String.class);
        profileDataConstructor.setAccessible(true);
        Object profileData = profileDataConstructor
            .newInstance("test@slack.com", "https://avatar.url", "U12345", "testuser", "Test User");

        var tokenResponseConstructor = slackTokenResponseClass
            .getDeclaredConstructor(String.class, String.class, Integer.class, String.class, String.class, String.class);
        tokenResponseConstructor.setAccessible(true);
        Object tokenResponse = tokenResponseConstructor
            .newInstance("xoxp-token", null, 43200, "T12345", "Test Team", null);

        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), anyString()))
            .thenReturn(Optional.empty());

        Method method = OAuthSlackService.class.getDeclaredMethod(
            "handleUserAuthentication",
            User.class,
            userProfileDataClass,
            slackTokenResponseClass
        );
        method.setAccessible(true);

        UserOAuthIdentity result = (UserOAuthIdentity) method.invoke(
            oauthSlackService,
            user,
            profileData,
            tokenResponse
        );

        assertNotNull(result);
        assertEquals(user, result.getUser());
        assertEquals("slack", result.getProvider());
    }

    // Test handleUserAuthentication - Existing OAuth
    @Test
    void testHandleUserAuthenticationExistingOAuth() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());

        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(user);
        existingOAuth.setProvider("slack");
        existingOAuth.setProviderUserId("U12345");

        Class<?> userProfileDataClass = Class.forName(
            "area.server.AREA_Back.service.Auth.OAuthSlackService$UserProfileData"
        );
        Class<?> slackTokenResponseClass = Class.forName(
            "area.server.AREA_Back.service.Auth.OAuthSlackService$SlackTokenResponse"
        );

        var profileDataConstructor = userProfileDataClass
            .getDeclaredConstructor(String.class, String.class, String.class, String.class, String.class);
        profileDataConstructor.setAccessible(true);
        Object profileData = profileDataConstructor
            .newInstance("test@slack.com", "https://avatar.url", "U12345", "testuser", "Test User");

        var tokenResponseConstructor = slackTokenResponseClass
            .getDeclaredConstructor(String.class, String.class, Integer.class, String.class, String.class, String.class);
        tokenResponseConstructor.setAccessible(true);
        Object tokenResponse = tokenResponseConstructor
            .newInstance("xoxp-new-token", null, 43200, "T12345", "Test Team", null);

        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("new-encrypted-token");
        when(userOAuthIdentityRepository.findByUserAndProvider(any(User.class), anyString()))
            .thenReturn(Optional.of(existingOAuth));

        Method method = OAuthSlackService.class.getDeclaredMethod(
            "handleUserAuthentication",
            User.class,
            userProfileDataClass,
            slackTokenResponseClass
        );
        method.setAccessible(true);

        UserOAuthIdentity result = (UserOAuthIdentity) method.invoke(
            oauthSlackService,
            user,
            profileData,
            tokenResponse
        );

        assertNotNull(result);
        assertEquals(existingOAuth, result);
        assertEquals("new-encrypted-token", result.getAccessTokenEnc());
    }

    // Test calculateExpirationTime - With valid expiresIn
    @Test
    void testCalculateExpirationTimeWithValidExpiresIn() throws Exception {
        Method method = OAuthSlackService.class.getDeclaredMethod("calculateExpirationTime", Integer.class);
        method.setAccessible(true);

        LocalDateTime before = LocalDateTime.now();
        LocalDateTime result = (LocalDateTime) method.invoke(oauthSlackService, 3600);
        LocalDateTime after = LocalDateTime.now().plusSeconds(3600);

        assertNotNull(result);
        assertTrue(result.isAfter(before) || result.isEqual(before));
        assertTrue(result.isBefore(after) || result.isEqual(after));
    }

    // Test calculateExpirationTime - With null expiresIn
    @Test
    void testCalculateExpirationTimeWithNullExpiresIn() throws Exception {
        Method method = OAuthSlackService.class.getDeclaredMethod("calculateExpirationTime", Integer.class);
        method.setAccessible(true);

        LocalDateTime before = LocalDateTime.now().plusDays(89);
        LocalDateTime result = (LocalDateTime) method.invoke(oauthSlackService, (Integer) null);
        LocalDateTime after = LocalDateTime.now().plusDays(91);

        assertNotNull(result);
        assertTrue(result.isAfter(before) || result.isEqual(before));
        assertTrue(result.isBefore(after) || result.isEqual(after));
    }

    // Test calculateExpirationTime - With zero expiresIn
    @Test
    void testCalculateExpirationTimeWithZeroExpiresIn() throws Exception {
        Method method = OAuthSlackService.class.getDeclaredMethod("calculateExpirationTime", Integer.class);
        method.setAccessible(true);

        LocalDateTime before = LocalDateTime.now().plusDays(89);
        LocalDateTime result = (LocalDateTime) method.invoke(oauthSlackService, 0);
        LocalDateTime after = LocalDateTime.now().plusDays(91);

        assertNotNull(result);
        assertTrue(result.isAfter(before) || result.isEqual(before));
        assertTrue(result.isBefore(after) || result.isEqual(after));
    }

    // Test calculateExpirationTime - With negative expiresIn
    @Test
    void testCalculateExpirationTimeWithNegativeExpiresIn() throws Exception {
        Method method = OAuthSlackService.class.getDeclaredMethod("calculateExpirationTime", Integer.class);
        method.setAccessible(true);

        LocalDateTime before = LocalDateTime.now().plusDays(89);
        LocalDateTime result = (LocalDateTime) method.invoke(oauthSlackService, -100);
        LocalDateTime after = LocalDateTime.now().plusDays(91);

        assertNotNull(result);
        assertTrue(result.isAfter(before) || result.isEqual(before));
        assertTrue(result.isBefore(after) || result.isEqual(after));
    }
}

