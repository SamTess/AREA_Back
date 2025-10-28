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
import area.server.AREA_Back.service.Auth.OAuthGithubService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthGithubServiceTest {

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
    private OAuthGithubService oauthGithubService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        // Setup JwtCookieProperties mock with lenient() to avoid UnnecessaryStubbingException
        lenient().when(jwtCookieProperties.getAccessTokenExpiry()).thenReturn(900);
        lenient().when(jwtCookieProperties.getRefreshTokenExpiry()).thenReturn(604800);
        lenient().when(jwtCookieProperties.isSecure()).thenReturn(false);
        lenient().when(jwtCookieProperties.getSameSite()).thenReturn("Strict");
        lenient().when(jwtCookieProperties.getDomain()).thenReturn(null);

        oauthGithubService = new OAuthGithubService(
            "test-client-id",
            "test-client-secret",
            "http://localhost:3000",
            jwtService,
            jwtCookieProperties,
            meterRegistry,
            redisTokenService,
            passwordEncoder,
            authService,
            restTemplate
        );
        // Manually initialize metrics since @PostConstruct won't run in tests
        try {
            var initMetricsMethod = OAuthGithubService.class.getDeclaredMethod("initMetrics");
            initMetricsMethod.setAccessible(true);
            initMetricsMethod.invoke(oauthGithubService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
        // Set the repositories via reflection since they're private
        try {
            var userRepoField = OAuthGithubService.class.getDeclaredField("userRepository");
            userRepoField.setAccessible(true);
            userRepoField.set(oauthGithubService, userRepository);

            var oauthRepoField = OAuthGithubService.class.getDeclaredField("userOAuthIdentityRepository");
            oauthRepoField.setAccessible(true);
            oauthRepoField.set(oauthGithubService, userOAuthIdentityRepository);

            var tokenEncryptionField = OAuthGithubService.class.getDeclaredField("tokenEncryptionService");
            tokenEncryptionField.setAccessible(true);
            tokenEncryptionField.set(oauthGithubService, tokenEncryptionService);
        } catch (Exception e) {
            fail("Failed to set up test dependencies: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        // Test that the service is properly initialized with metrics
        assertNotNull(oauthGithubService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testAuthenticateWithInvalidCode() {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("invalid-code");

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGithubService.authenticate(request, httpServletResponse);
        });
    }

    @Test
    void testLinkToExistingUserWithInvalidCode() {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@example.com");

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGithubService.linkToExistingUser(existingUser, "invalid-code");
        });
    }

    @Test
    void testAuthenticateSuccess() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("github-user@example.com");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("github-user@example.com");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Login successful");
        authResponse.setUser(userResponse);

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", "github-user@example.com");
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock AuthService
        when(authService.oauthLogin(eq("github-user@example.com"), anyString(), eq(httpServletResponse)))
            .thenReturn(authResponse);

        // Mock UserRepository
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Mock token encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock OAuth identity repository
        UserOAuthIdentity oauthIdentity = new UserOAuthIdentity();
        oauthIdentity.setId(UUID.randomUUID());
        oauthIdentity.setUser(user);
        oauthIdentity.setProvider("github");
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(oauthIdentity);

        // When
        AuthResponse result = oauthGithubService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        assertEquals("Login successful", result.getMessage());
        assertEquals(userId, result.getUser().getId());
        verify(authService).oauthLogin(eq("github-user@example.com"), anyString(), eq(httpServletResponse));
        verify(userOAuthIdentityRepository).save(any(UserOAuthIdentity.class));
    }

    @Test
    void testAuthenticateWithNoEmail() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("github-12345@oauth.placeholder");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("github-12345@oauth.placeholder");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Login successful");
        authResponse.setUser(userResponse);

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch without email
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", null);
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock email fetch endpoint
        ResponseEntity<List<Map<String, Object>>> emailsResponse = new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        when(restTemplate.exchange(
            eq("https://api.github.com/user/emails"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(emailsResponse);

        // Mock AuthService
        when(authService.oauthLogin(eq("github-12345@oauth.placeholder"), anyString(), eq(httpServletResponse)))
            .thenReturn(authResponse);

        // Mock UserRepository
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Mock token encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock OAuth identity repository
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(new UserOAuthIdentity());

        // When
        AuthResponse result = oauthGithubService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        verify(authService).oauthLogin(eq("github-12345@oauth.placeholder"), anyString(), eq(httpServletResponse));
    }

    @Test
    void testAuthenticateWithEmailFromEmailsEndpoint() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("primary@example.com");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("primary@example.com");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Login successful");
        authResponse.setUser(userResponse);

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch without email
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", "");
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock email fetch endpoint with primary verified email
        List<Map<String, Object>> emails = new ArrayList<>();
        Map<String, Object> primaryEmail = new HashMap<>();
        primaryEmail.put("email", "primary@example.com");
        primaryEmail.put("primary", true);
        primaryEmail.put("verified", true);
        emails.add(primaryEmail);

        Map<String, Object> secondaryEmail = new HashMap<>();
        secondaryEmail.put("email", "secondary@example.com");
        secondaryEmail.put("primary", false);
        secondaryEmail.put("verified", false);
        emails.add(secondaryEmail);

        ResponseEntity<List<Map<String, Object>>> emailsResponse = new ResponseEntity<>(emails, HttpStatus.OK);
        when(restTemplate.exchange(
            eq("https://api.github.com/user/emails"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(emailsResponse);

        // Mock AuthService
        when(authService.oauthLogin(eq("primary@example.com"), anyString(), eq(httpServletResponse)))
            .thenReturn(authResponse);

        // Mock UserRepository
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Mock token encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock OAuth identity repository
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(new UserOAuthIdentity());

        // When
        AuthResponse result = oauthGithubService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        verify(authService).oauthLogin(eq("primary@example.com"), anyString(), eq(httpServletResponse));
    }

    @Test
    void testAuthenticateWithEmailsFetchFailure() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("github-12345@oauth.placeholder");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("github-12345@oauth.placeholder");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Login successful");
        authResponse.setUser(userResponse);

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch without email
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", null);
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock email fetch endpoint to throw exception
        when(restTemplate.exchange(
            eq("https://api.github.com/user/emails"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("API error"));

        // Mock AuthService
        when(authService.oauthLogin(eq("github-12345@oauth.placeholder"), anyString(), eq(httpServletResponse)))
            .thenReturn(authResponse);

        // Mock UserRepository
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Mock token encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock OAuth identity repository
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(new UserOAuthIdentity());

        // When
        AuthResponse result = oauthGithubService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        verify(authService).oauthLogin(eq("github-12345@oauth.placeholder"), anyString(), eq(httpServletResponse));
    }

    @Test
    void testAuthenticateWithExistingOAuthIdentity() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("github-user@example.com");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("github-user@example.com");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Login successful");
        authResponse.setUser(userResponse);

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", "github-user@example.com");
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock AuthService
        when(authService.oauthLogin(eq("github-user@example.com"), anyString(), eq(httpServletResponse)))
            .thenReturn(authResponse);

        // Mock UserRepository
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Mock token encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock existing OAuth identity
        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setId(UUID.randomUUID());
        existingOAuth.setUser(user);
        existingOAuth.setProvider("github");
        existingOAuth.setProviderUserId("old-id");

        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.of(existingOAuth));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(existingOAuth);

        // When
        AuthResponse result = oauthGithubService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        verify(userOAuthIdentityRepository).save(argThat(oauth -> 
            oauth.getProviderUserId().equals("12345")
        ));
    }

    @Test
    void testAuthenticateFailure() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch to throw exception
        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("API error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGithubService.authenticate(request, httpServletResponse);
        });
    }

    @Test
    void testAuthenticateUserNotFoundAfterLogin() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        UUID userId = UUID.randomUUID();
        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("github-user@example.com");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Login successful");
        authResponse.setUser(userResponse);

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", "github-user@example.com");
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock AuthService
        when(authService.oauthLogin(eq("github-user@example.com"), anyString(), eq(httpServletResponse)))
            .thenReturn(authResponse);

        // Mock UserRepository to return empty
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGithubService.authenticate(request, httpServletResponse);
        });
    }

    @Test
    void testLinkToExistingUserSuccess() throws Exception {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@example.com");

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", "github-user@example.com");
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock token encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock OAuth identity repository
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("github", "12345")).thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "github")).thenReturn(Optional.empty());
        
        UserOAuthIdentity savedOAuth = new UserOAuthIdentity();
        savedOAuth.setId(UUID.randomUUID());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(savedOAuth);

        // When
        UserOAuthIdentity result = oauthGithubService.linkToExistingUser(existingUser, "valid-code");

        // Then
        assertNotNull(result);
        verify(userOAuthIdentityRepository).save(any(UserOAuthIdentity.class));
    }

    @Test
    void testLinkToExistingUserWithNoEmail() throws Exception {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@example.com");

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch without email
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", null);
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock email fetch endpoint
        ResponseEntity<List<Map<String, Object>>> emailsResponse = new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        when(restTemplate.exchange(
            eq("https://api.github.com/user/emails"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(emailsResponse);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGithubService.linkToExistingUser(existingUser, "valid-code");
        });
        assertTrue(exception.getMessage().contains("email is required"));
    }

    @Test
    void testLinkToExistingUserAlreadyLinked() throws Exception {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@example.com");

        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setEmail("other@example.com");

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", "github-user@example.com");
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock existing OAuth identity for different user
        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setUser(otherUser);
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("github", "12345"))
            .thenReturn(Optional.of(existingOAuth));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGithubService.linkToExistingUser(existingUser, "valid-code");
        });
        assertTrue(exception.getMessage().contains("already linked"));
    }

    @Test
    void testLinkToExistingUserUpdateExisting() throws Exception {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@example.com");

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", "github-user@example.com");
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock token encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock existing OAuth identity for same user
        UserOAuthIdentity existingOAuth = new UserOAuthIdentity();
        existingOAuth.setId(UUID.randomUUID());
        existingOAuth.setUser(existingUser);
        existingOAuth.setProvider("github");
        existingOAuth.setProviderUserId("old-id");

        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("github", "12345")).thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "github")).thenReturn(Optional.of(existingOAuth));
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(existingOAuth);

        // When
        UserOAuthIdentity result = oauthGithubService.linkToExistingUser(existingUser, "valid-code");

        // Then
        assertNotNull(result);
        verify(userOAuthIdentityRepository).save(argThat(oauth -> 
            oauth.getProviderUserId().equals("12345")
        ));
    }

    @Test
    void testLinkToExistingUserSaveFailure() throws Exception {
        // Given
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("test@example.com");

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", "github-user@example.com");
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock token encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock OAuth identity repository
        when(userOAuthIdentityRepository.findByProviderAndProviderUserId("github", "12345")).thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.findByUserAndProvider(existingUser, "github")).thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenThrow(new RuntimeException("Database error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            oauthGithubService.linkToExistingUser(existingUser, "valid-code");
        });
        assertTrue(exception.getMessage().contains("Failed to link GitHub account"));
    }

    @Test
    void testExchangeCodeForTokenNoAccessToken() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange without access_token
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("error", "invalid_grant");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGithubService.authenticate(request, httpServletResponse);
        });
    }

    @Test
    void testExchangeCodeForTokenFailedResponse() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange with error response
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGithubService.authenticate(request, httpServletResponse);
        });
    }

    @Test
    void testFetchUserProfileMissingId() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch without id
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("email", "github-user@example.com");
        userProfileBody.put("avatar_url", "https://avatar.url");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGithubService.authenticate(request, httpServletResponse);
        });
    }

    @Test
    void testFetchUserProfileFailedResponse() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch with error
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            oauthGithubService.authenticate(request, httpServletResponse);
        });
    }

    @Test
    void testExtractEmailWithNonPrimaryEmail() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setAuthorizationCode("valid-code");

        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("fallback@example.com");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userId);
        userResponse.setEmail("fallback@example.com");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Login successful");
        authResponse.setUser(userResponse);

        // Mock token exchange
        Map<String, Object> tokenResponseBody = new HashMap<>();
        tokenResponseBody.put("access_token", "github-access-token");
        ResponseEntity<Map<String, Object>> tokenResponse = new ResponseEntity<>(tokenResponseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://github.com/login/oauth/access_token"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(tokenResponse);

        // Mock user profile fetch without email
        Map<String, Object> userProfileBody = new HashMap<>();
        userProfileBody.put("id", 12345);
        userProfileBody.put("email", null);
        userProfileBody.put("avatar_url", "https://avatar.url");
        userProfileBody.put("name", "GitHub User");
        userProfileBody.put("login", "githubuser");
        ResponseEntity<Map<String, Object>> userProfileResponse = new ResponseEntity<>(userProfileBody, HttpStatus.OK);

        when(restTemplate.exchange(
            eq("https://api.github.com/user"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(userProfileResponse);

        // Mock email fetch endpoint with non-primary email
        List<Map<String, Object>> emails = new ArrayList<>();
        Map<String, Object> email1 = new HashMap<>();
        email1.put("email", "fallback@example.com");
        email1.put("primary", false);
        email1.put("verified", true);
        emails.add(email1);

        ResponseEntity<List<Map<String, Object>>> emailsResponse = new ResponseEntity<>(emails, HttpStatus.OK);
        when(restTemplate.exchange(
            eq("https://api.github.com/user/emails"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(emailsResponse);

        // Mock AuthService
        when(authService.oauthLogin(eq("fallback@example.com"), anyString(), eq(httpServletResponse)))
            .thenReturn(authResponse);

        // Mock UserRepository
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Mock token encryption
        when(tokenEncryptionService.encryptToken(anyString())).thenReturn("encrypted-token");

        // Mock OAuth identity repository
        when(userOAuthIdentityRepository.findByUserAndProvider(user, "github")).thenReturn(Optional.empty());
        when(userOAuthIdentityRepository.save(any(UserOAuthIdentity.class))).thenReturn(new UserOAuthIdentity());

        // When
        AuthResponse result = oauthGithubService.authenticate(request, httpServletResponse);

        // Then
        assertNotNull(result);
        verify(authService).oauthLogin(eq("fallback@example.com"), anyString(), eq(httpServletResponse));
    }
}