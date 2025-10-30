package area.server.AREA_Back.service;

import area.server.AREA_Back.config.JwtCookieProperties;
import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.service.Auth.AuthService;
import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.Auth.OAuthDiscordService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import area.server.AREA_Back.service.Webhook.DiscordWelcomeService;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthDiscordServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private UserRepository userRepository;

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

    @Mock
    private DiscordWelcomeService discordWelcomeService;

    private MeterRegistry meterRegistry;
    private OAuthDiscordService oauthDiscordService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        // Setup JwtCookieProperties mock with lenient() to avoid UnnecessaryStubbingException
        lenient().when(jwtCookieProperties.getAccessTokenExpiry()).thenReturn(900);
        lenient().when(jwtCookieProperties.getRefreshTokenExpiry()).thenReturn(604800);
        lenient().when(jwtCookieProperties.isSecure()).thenReturn(false);
        lenient().when(jwtCookieProperties.getSameSite()).thenReturn("Strict");
        lenient().when(jwtCookieProperties.getDomain()).thenReturn(null);

        // Setup DiscordWelcomeService mock
        lenient().doNothing().when(discordWelcomeService).sendWelcomeMessagesToNewGuilds(anyString());

        oauthDiscordService = new OAuthDiscordService(
            "test-client-id",
            "test-client-secret",
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
            discordWelcomeService,
            restTemplate
        );

        try {
            var initMetricsMethod = OAuthDiscordService.class.getDeclaredMethod("initMetrics");
            initMetricsMethod.setAccessible(true);
            initMetricsMethod.invoke(oauthDiscordService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(oauthDiscordService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testProviderKeyIsDiscord() {
        assertEquals("discord", oauthDiscordService.getProviderKey());
    }

    @Test
    void testProviderLabelIsDiscord() {
        assertEquals("Discord", oauthDiscordService.getProviderLabel());
    }

    @Test
    void testAuthorizationUrlContainsDiscordEndpoint() {
        String authUrl = oauthDiscordService.getUserAuthUrl();
        assertTrue(authUrl.contains("discord.com/api/oauth2/authorize"));
        assertTrue(authUrl.contains("client_id=test-client-id"));
        assertTrue(authUrl.contains("redirect_uri=http://localhost:3000/oauth-callback"));
    }

    @Test
    void testAuthorizationUrlContainsRequiredScopes() {
        String authUrl = oauthDiscordService.getUserAuthUrl();
        assertTrue(authUrl.contains("identify"));
        assertTrue(authUrl.contains("email"));
        assertTrue(authUrl.contains("guilds"));
    }

    @Test
    void testClientIdConfiguration() {
        assertEquals("test-client-id", oauthDiscordService.getClientId());
    }

    @Test
    void testClientSecretConfiguration() {
        assertEquals("test-client-secret", oauthDiscordService.getClientSecret());
    }

    @Test
    void testMetricsRegistration() {
        assertNotNull(meterRegistry.find("oauth.discord.login.success").counter());
        assertNotNull(meterRegistry.find("oauth.discord.login.failure").counter());
        assertNotNull(meterRegistry.find("oauth.discord.authenticate.calls").counter());
        assertNotNull(meterRegistry.find("oauth.discord.token_exchange.calls").counter());
        assertNotNull(meterRegistry.find("oauth.discord.token_exchange.failures").counter());
    }

    @Test
    void testProviderLogoUrl() {
        String logoUrl = oauthDiscordService.getProviderLogoUrl();
        assertNotNull(logoUrl);
        assertTrue(logoUrl.contains("discord"));
    }

    // ===========================
    // Tests for authenticate
    // ===========================

    @Test
    void testAuthenticateSuccess() throws Exception {
        // Arrange
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("test-auth-code");
        
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "discord-user-id-123");
        userProfileBody.put("email", "test@discord.com");
        userProfileBody.put("username", "testuser");
        userProfileBody.put("discriminator", "1234");
        userProfileBody.put("global_name", "Test User");
        userProfileBody.put("avatar", "avatar-hash");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        // Mock user and auth response
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("test@discord.com");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("test@discord.com");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Login successful");
        authResponse.setUser(userResponse);

        when(authService.oauthLogin(eq("test@discord.com"), anyString(), eq(response))).thenReturn(authResponse);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenAnswer(i -> "encrypted-" + i.getArguments()[0]);
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "discord")).thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        AuthResponse result = oauthDiscordService.authenticate(request, response);

        // Assert
        assertNotNull(result);
        assertEquals("Login successful", result.getMessage());
        verify(authService, times(1)).oauthLogin(eq("test@discord.com"), anyString(), eq(response));
        verify(userOAuthIdentityRepository, times(1)).save(any(UserOAuthIdentity.class));
        assertEquals(1.0, meterRegistry.find("oauth.discord.authenticate.calls").counter().count());
        assertEquals(1.0, meterRegistry.find("oauth.discord.login.success").counter().count());
    }

    @Test
    void testAuthenticateFailsWhenEmailIsMissing() throws Exception {
        // Arrange
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("test-auth-code");
        
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        // Mock user profile response without email
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "discord-user-id-123");
        userProfileBody.put("username", "testuser");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthDiscordService.authenticate(request, response);
        });

        assertTrue(exception.getMessage().contains("email is required"));
        // Le compteur est incrémenté deux fois : une fois pour l'email manquant (ligne 134) et une fois dans le catch (ligne 161)
        assertEquals(2.0, meterRegistry.find("oauth.discord.login.failure").counter().count());
    }

    @Test
    void testAuthenticateHandlesTokenExchangeFailure() {
        // Arrange
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("invalid-code");
        
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("Token exchange failed"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthDiscordService.authenticate(request, response);
        });

        assertTrue(exception.getMessage().contains("OAuth authentication failed"));
        assertEquals(1.0, meterRegistry.find("oauth.discord.token_exchange.failures").counter().count());
    }

    // ===========================
    // Tests for linkToExistingUser
    // ===========================

    @Test
    void testLinkToExistingUserSuccess() throws Exception {
        // Arrange
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("existing@test.com");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("refresh_token", "test-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "discord-user-id-456");
        userProfileBody.put("email", "discord@test.com");
        userProfileBody.put("username", "discorduser");
        userProfileBody.put("discriminator", "5678");
        userProfileBody.put("global_name", "Discord User");
        userProfileBody.put("avatar", "avatar-hash-2");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        when(tokenEncryptionService.encryptToken(anyString())).thenAnswer(i -> "encrypted-" + i.getArguments()[0]);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("discord", "discord-user-id-456"))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "discord"))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        UserOAuthIdentity result = oauthDiscordService.linkToExistingUser(existingUser, "test-auth-code");

        // Assert
        assertNotNull(result);
        assertEquals(existingUser, result.getUser());
        assertEquals("discord", result.getProvider());
        assertEquals("discord-user-id-456", result.getProviderUserId());
        verify(userOAuthIdentityRepository, times(1)).save(any(UserOAuthIdentity.class));
    }

    @Test
    void testLinkToExistingUserUpdatesExistingOAuth() throws Exception {
        // Arrange
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("existing@test.com");

        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setId(UUID.randomUUID());
        existingOAuth.setUser(existingUser);
        existingOAuth.setProvider("discord");
        existingOAuth.setProviderUserId("old-discord-id");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "new-access-token");
        tokenResponseBody.put("refresh_token", "new-refresh-token");
        tokenResponseBody.put("expires_in", 7200);
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "new-discord-id");
        userProfileBody.put("email", "discord@test.com");
        userProfileBody.put("username", "newdiscorduser");
        userProfileBody.put("discriminator", "9999");
        userProfileBody.put("global_name", "New Discord User");
        userProfileBody.put("avatar", "new-avatar-hash");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        when(tokenEncryptionService.encryptToken(anyString())).thenAnswer(i -> "encrypted-" + i.getArguments()[0]);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("discord", "new-discord-id"))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "discord"))
            .thenReturn(Optional.of(existingOAuth));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        UserOAuthIdentity result = oauthDiscordService.linkToExistingUser(existingUser, "test-auth-code");

        // Assert
        assertNotNull(result);
        assertEquals("new-discord-id", result.getProviderUserId());
        verify(userOAuthIdentityRepository, times(1)).save(any(UserOAuthIdentity.class));
    }

    @Test
    void testLinkToExistingUserFailsWhenEmailIsMissing() throws Exception {
        // Arrange
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        // Mock user profile response without email
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "discord-user-id-789");
        userProfileBody.put("username", "testuser");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthDiscordService.linkToExistingUser(existingUser, "test-auth-code");
        });

        assertTrue(exception.getMessage().contains("email is required"));
    }

    @Test
    void testLinkToExistingUserFailsWhenAccountAlreadyLinked() throws Exception {
        // Arrange
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());

        UserOAuthIdentity otherUserOAuth = new UserOAuthIdentity();
        otherUserOAuth.setUser(otherUser);
        otherUserOAuth.setProvider("discord");
        otherUserOAuth.setProviderUserId("discord-user-id-linked");

        // Mock token exchange response
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        // Mock user profile response
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "discord-user-id-linked");
        userProfileBody.put("email", "discord@test.com");
        userProfileBody.put("username", "linkeduser");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("discord", "discord-user-id-linked"))
            .thenReturn(Optional.of(otherUserOAuth));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthDiscordService.linkToExistingUser(existingUser, "test-auth-code");
        });

        assertTrue(exception.getMessage().contains("already linked to another user"));
    }

    // ===========================
    // Tests for calculateExpirationTime (private method - test via reflection or indirectly)
    // ===========================

    @Test
    void testCalculateExpirationTimeWithValidExpiresIn() throws Exception {
        // We'll test this indirectly through linkToExistingUser
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        tokenResponseBody.put("expires_in", 7200); // 2 hours
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "discord-id-calc");
        userProfileBody.put("email", "calc@test.com");
        userProfileBody.put("username", "calcuser");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        when(tokenEncryptionService.encryptToken(anyString())).thenAnswer(i -> "encrypted-" + i.getArguments()[0]);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());

        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        when(userOAuthIdentityRepository.save(captor.capture())).thenAnswer(i -> i.getArguments()[0]);

        // Act
        oauthDiscordService.linkToExistingUser(existingUser, "test-code");

        // Assert
        UserOAuthIdentity saved = captor.getValue();
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void testCalculateExpirationTimeWithNullExpiresIn() throws Exception {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-access-token");
        // No expires_in field
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "discord-id-null");
        userProfileBody.put("email", "null@test.com");
        userProfileBody.put("username", "nulluser");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        when(tokenEncryptionService.encryptToken(anyString())).thenAnswer(i -> "encrypted-" + i.getArguments()[0]);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());

        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        when(userOAuthIdentityRepository.save(captor.capture())).thenAnswer(i -> i.getArguments()[0]);

        // Act
        oauthDiscordService.linkToExistingUser(existingUser, "test-code");

        // Assert - should use default 7 days
        UserOAuthIdentity saved = captor.getValue();
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now().plusDays(6)));
    }

    // ===========================
    // Tests for exchangeCodeForToken (private method - tested indirectly)
    // ===========================

    @Test
    void testExchangeCodeForTokenWithAllFields() throws Exception {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "full-access-token");
        tokenResponseBody.put("refresh_token", "full-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "discord-full");
        userProfileBody.put("email", "full@test.com");
        userProfileBody.put("username", "fulluser");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        when(tokenEncryptionService.encryptToken(anyString())).thenAnswer(i -> "encrypted-" + i.getArguments()[0]);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        UserOAuthIdentity result = oauthDiscordService.linkToExistingUser(existingUser, "test-code");

        // Assert
        assertNotNull(result);
        verify(restTemplate, times(1)).exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        );
    }

    @Test
    void testExchangeCodeForTokenWithoutRefreshToken() throws Exception {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "access-only-token");
        // No refresh_token
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "discord-no-refresh");
        userProfileBody.put("email", "norefresh@test.com");
        userProfileBody.put("username", "norefreshuser");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        when(tokenEncryptionService.encryptToken(anyString())).thenAnswer(i -> "encrypted-" + i.getArguments()[0]);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());

        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        when(userOAuthIdentityRepository.save(captor.capture())).thenAnswer(i -> i.getArguments()[0]);

        // Act
        oauthDiscordService.linkToExistingUser(existingUser, "test-code");

        // Assert
        UserOAuthIdentity saved = captor.getValue();
        assertNotNull(saved);
        // Refresh token might be null or not set
    }

    @Test
    void testExchangeCodeForTokenFailsWithBadResponse() {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        // Mock a failed response
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthDiscordService.linkToExistingUser(existingUser, "bad-code");
        });

        assertTrue(exception.getMessage().contains("Failed to exchange"));
        // Le compteur est incrémenté deux fois : une fois à la ligne 306 et une fois dans le catch à la ligne 337
        assertEquals(2.0, meterRegistry.find("oauth.discord.token_exchange.failures").counter().count());
    }

    @Test
    void testExchangeCodeForTokenFailsWithNoAccessToken() {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        // Missing access_token
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthDiscordService.linkToExistingUser(existingUser, "test-code");
        });

        assertTrue(exception.getMessage().contains("No access_token"));
    }

    // ===========================
    // Tests for fetchUserProfile (private method - tested indirectly)
    // ===========================

    @Test
    void testFetchUserProfileWithAllFields() throws Exception {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-token");
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "full-profile-id");
        userProfileBody.put("email", "fullprofile@test.com");
        userProfileBody.put("username", "fullprofileuser");
        userProfileBody.put("discriminator", "1111");
        userProfileBody.put("global_name", "Full Profile User");
        userProfileBody.put("avatar", "full-avatar-hash");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        when(tokenEncryptionService.encryptToken(anyString())).thenAnswer(i -> "encrypted-" + i.getArguments()[0]);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());

        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        when(userOAuthIdentityRepository.save(captor.capture())).thenAnswer(i -> i.getArguments()[0]);

        // Act
        oauthDiscordService.linkToExistingUser(existingUser, "test-code");

        // Assert
        UserOAuthIdentity saved = captor.getValue();
        Map<String, Object> meta = saved.getTokenMeta();
        assertEquals("fullprofile@test.com", meta.get("email"));
        assertEquals("fullprofileuser", meta.get("username"));
        assertEquals("1111", meta.get("discriminator"));
        assertEquals("Full Profile User", meta.get("global_name"));
        assertTrue(meta.get("avatar_url").toString().contains("full-avatar-hash"));
    }

    @Test
    void testFetchUserProfileWithMinimalFields() throws Exception {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-token");
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "minimal-id");
        userProfileBody.put("email", "minimal@test.com");
        // Missing optional fields
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        when(tokenEncryptionService.encryptToken(anyString())).thenAnswer(i -> "encrypted-" + i.getArguments()[0]);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(any(), anyString()))
            .thenReturn(Optional.empty());

        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        when(userOAuthIdentityRepository.save(captor.capture())).thenAnswer(i -> i.getArguments()[0]);

        // Act
        oauthDiscordService.linkToExistingUser(existingUser, "test-code");

        // Assert
        UserOAuthIdentity saved = captor.getValue();
        Map<String, Object> meta = saved.getTokenMeta();
        assertEquals("minimal@test.com", meta.get("email"));
        assertEquals("minimal-id", saved.getProviderUserId());
    }

    @Test
    void testFetchUserProfileFailsWhenIdIsMissing() {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-token");
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> userProfileBody = new HashMap<>();
        // Missing id
        userProfileBody.put("email", "noid@test.com");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthDiscordService.linkToExistingUser(existingUser, "test-code");
        });

        assertTrue(exception.getMessage().contains("id is missing"));
    }

    @Test
    void testFetchUserProfileFailsWithBadResponse() {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "test-token");
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthDiscordService.linkToExistingUser(existingUser, "test-code");
        });

        assertTrue(exception.getMessage().contains("Failed to fetch"));
    }

    // ===========================
    // Tests for handleUserAuthentication (private method - tested indirectly through authenticate)
    // ===========================

    @Test
    void testHandleUserAuthenticationCreatesNewOAuth() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("test-code-new");
        
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "new-oauth-token");
        tokenResponseBody.put("refresh_token", "new-refresh-token");
        tokenResponseBody.put("expires_in", 3600);
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "new-oauth-id");
        userProfileBody.put("email", "newoauth@test.com");
        userProfileBody.put("username", "newoauthuser");
        userProfileBody.put("discriminator", "2222");
        userProfileBody.put("global_name", "New OAuth User");
        userProfileBody.put("avatar", "new-oauth-avatar");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("newoauth@test.com");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("newoauth@test.com");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Success");
        authResponse.setUser(userResponse);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        when(authService.oauthLogin(eq("newoauth@test.com"), anyString(), eq(response))).thenReturn(authResponse);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenAnswer(i -> "encrypted-" + i.getArguments()[0]);
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "discord")).thenReturn(Optional.empty());

        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        when(userOAuthIdentityRepository.save(captor.capture())).thenAnswer(i -> i.getArguments()[0]);

        // Act
        oauthDiscordService.authenticate(request, response);

        // Assert
        UserOAuthIdentity saved = captor.getValue();
        assertNotNull(saved);
        assertEquals("new-oauth-id", saved.getProviderUserId());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void testHandleUserAuthenticationUpdatesExistingOAuth() throws Exception {
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("test-code-update");
        
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);

        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "updated-token");
        tokenResponseBody.put("refresh_token", "updated-refresh");
        tokenResponseBody.put("expires_in", 7200);
        ResponseEntity<Map<String, Object>> tokenResponseEntity = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", "updated-oauth-id");
        userProfileBody.put("email", "updated@test.com");
        userProfileBody.put("username", "updateduser");
        ResponseEntity<Map<String, Object>> userProfileResponseEntity = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("updated@test.com");

        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setId(UUID.randomUUID());
        existingOAuth.setUser(user);
        existingOAuth.setProvider("discord");
        existingOAuth.setProviderUserId("old-id");
        existingOAuth.setCreatedAt(LocalDateTime.now().minusDays(10));

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("updated@test.com");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Success");
        authResponse.setUser(userResponse);

        when(restTemplate.exchange(
            eq("https://discord.com/api/oauth2/token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
            eq("https://discord.com/api/users/@me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponseEntity);

        when(authService.oauthLogin(eq("updated@test.com"), anyString(), eq(response))).thenReturn(authResponse);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenEncryptionService.encryptToken(anyString())).thenAnswer(i -> "encrypted-" + i.getArguments()[0]);
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "discord")).thenReturn(Optional.of(existingOAuth));

        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        when(userOAuthIdentityRepository.save(captor.capture())).thenAnswer(i -> i.getArguments()[0]);

        // Act
        oauthDiscordService.authenticate(request, response);

        // Assert
        UserOAuthIdentity saved = captor.getValue();
        assertNotNull(saved);
        assertEquals("updated-oauth-id", saved.getProviderUserId());
        assertNotNull(saved.getUpdatedAt());
    }
}
