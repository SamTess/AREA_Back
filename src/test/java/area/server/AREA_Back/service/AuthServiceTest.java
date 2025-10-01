package area.server.AREA_Back.service;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.LocalLoginRequest;
import area.server.AREA_Back.dto.RegisterRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserLocalIdentity;
import area.server.AREA_Back.repository.UserLocalIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserLocalIdentityRepository userLocalIdentityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private AuthService authService;

    private UUID testUserId;
    private String testEmail;
    private String testPassword;
    private String testAccessToken;
    private String testRefreshToken;
    private User testUser;
    private UserLocalIdentity testLocalIdentity;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            userLocalIdentityRepository,
            passwordEncoder,
            jwtService,
            redisTokenService
        );

        testUserId = UUID.randomUUID();
        testEmail = "test@example.com";
        testPassword = "password123";
        testAccessToken = "test.access.token";
        testRefreshToken = "test.refresh.token";

        testUser = new User();
        testUser.setId(testUserId);
        testUser.setEmail(testEmail);
        testUser.setIsActive(true);
        testUser.setIsAdmin(false);
        testUser.setCreatedAt(LocalDateTime.now());

        testLocalIdentity = new UserLocalIdentity();
        testLocalIdentity.setUser(testUser);
        testLocalIdentity.setEmail(testEmail);
        testLocalIdentity.setPasswordHash("hashedPassword");
        testLocalIdentity.setIsEmailVerified(false);
        testLocalIdentity.setFailedLoginAttempts(0);
        testLocalIdentity.setCreatedAt(LocalDateTime.now());
        testLocalIdentity.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void registerShouldCreateUserAndReturnAuthResponseWhenValidRequest() {
        // Given
        RegisterRequest registerRequest = new RegisterRequest(testEmail, testPassword, "avatar.jpg");

        when(userLocalIdentityRepository.existsByEmail(testEmail)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(passwordEncoder.encode(testPassword)).thenReturn("hashedPassword");
        when(jwtService.generateAccessToken(testUserId, testEmail)).thenReturn(testAccessToken);
        when(jwtService.generateRefreshToken(testUserId, testEmail)).thenReturn(testRefreshToken);

        // When
        AuthResponse authResponse = authService.register(registerRequest, response);

        // Then
        assertNotNull(authResponse);
        assertEquals("User registered successfully", authResponse.getMessage());
        assertNotNull(authResponse.getUser());
        assertEquals(testEmail, authResponse.getUser().getEmail());

        // Verify user creation
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture()); // Once for creation, once for last login update
        User savedUser = userCaptor.getAllValues().get(0);
        assertEquals(testEmail, savedUser.getEmail());
        assertTrue(savedUser.getIsActive());
        assertFalse(savedUser.getIsAdmin());

        // Verify local identity creation
        verify(userLocalIdentityRepository).save(any(UserLocalIdentity.class));
        verify(passwordEncoder).encode(testPassword);

        // Verify token operations
        verify(jwtService).generateAccessToken(testUserId, testEmail);
        verify(jwtService).generateRefreshToken(testUserId, testEmail);
        verify(redisTokenService).storeAccessToken(testAccessToken, testUserId);
        verify(redisTokenService).storeRefreshToken(testUserId, testRefreshToken);

        // Verify cookies are set
        verify(response, times(2)).addCookie(any(Cookie.class));
    }

    @Test
    void registerShouldThrowExceptionWhenEmailAlreadyExists() {
        // Given
        RegisterRequest registerRequest = new RegisterRequest(testEmail, testPassword, null);
        when(userLocalIdentityRepository.existsByEmail(testEmail)).thenReturn(true);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.register(registerRequest, response));
        assertEquals("Email already registered", exception.getMessage());

        verify(userRepository, never()).save(any());
        verify(userLocalIdentityRepository, never()).save(any());
    }

    @Test
    void loginShouldReturnAuthResponseWhenValidCredentials() {
        // Given
        LocalLoginRequest loginRequest = new LocalLoginRequest(testEmail, testPassword);

        when(userLocalIdentityRepository.findByEmail(testEmail)).thenReturn(Optional.of(testLocalIdentity));
        when(passwordEncoder.matches(testPassword, "hashedPassword")).thenReturn(true);
        when(jwtService.generateAccessToken(testUserId, testEmail)).thenReturn(testAccessToken);
        when(jwtService.generateRefreshToken(testUserId, testEmail)).thenReturn(testRefreshToken);

        // When
        AuthResponse authResponse = authService.login(loginRequest, response);

        // Then
        assertNotNull(authResponse);
        assertEquals("Login successful", authResponse.getMessage());
        assertNotNull(authResponse.getUser());
        assertEquals(testEmail, authResponse.getUser().getEmail());

        // Verify password check
        verify(passwordEncoder).matches(testPassword, "hashedPassword");

        // Verify failed attempts reset
        verify(userLocalIdentityRepository).resetFailedLoginAttempts(testEmail);

        // Verify token operations
        verify(jwtService).generateAccessToken(testUserId, testEmail);
        verify(jwtService).generateRefreshToken(testUserId, testEmail);
        verify(redisTokenService).storeAccessToken(testAccessToken, testUserId);
        verify(redisTokenService).storeRefreshToken(testUserId, testRefreshToken);

        // Verify last login update
        verify(userRepository).save(testUser);

        // Verify cookies are set
        verify(response, times(2)).addCookie(any(Cookie.class));
    }

    @Test
    void loginShouldThrowExceptionWhenEmailNotFound() {
        // Given
        LocalLoginRequest loginRequest = new LocalLoginRequest(testEmail, testPassword);
        when(userLocalIdentityRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.login(loginRequest, response));
        assertEquals("Invalid credentials", exception.getMessage());

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    void loginShouldThrowExceptionWhenAccountIsLocked() {
        // Given
        LocalLoginRequest loginRequest = new LocalLoginRequest(testEmail, testPassword);
        testLocalIdentity.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        when(userLocalIdentityRepository.findByEmail(testEmail)).thenReturn(Optional.of(testLocalIdentity));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.login(loginRequest, response));
        assertEquals("Account is temporarily locked due to failed login attempts", exception.getMessage());

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void loginShouldThrowExceptionWhenUserIsInactive() {
        // Given
        LocalLoginRequest loginRequest = new LocalLoginRequest(testEmail, testPassword);
        testUser.setIsActive(false);

        when(userLocalIdentityRepository.findByEmail(testEmail)).thenReturn(Optional.of(testLocalIdentity));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.login(loginRequest, response));
        assertEquals("Account is inactive", exception.getMessage());

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void loginShouldIncrementFailedAttemptsWhenWrongPassword() {
        // Given
        LocalLoginRequest loginRequest = new LocalLoginRequest(testEmail, "wrongPassword");

        when(userLocalIdentityRepository.findByEmail(testEmail)).thenReturn(Optional.of(testLocalIdentity));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.login(loginRequest, response));
        assertEquals("Invalid credentials", exception.getMessage());

        verify(userLocalIdentityRepository).incrementFailedLoginAttempts(testEmail);
        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    void loginShouldLockAccountWhenTooManyFailedAttempts() {
        // Given
        LocalLoginRequest loginRequest = new LocalLoginRequest(testEmail, "wrongPassword");
        testLocalIdentity.setFailedLoginAttempts(4); // 5th attempt will lock

        when(userLocalIdentityRepository.findByEmail(testEmail)).thenReturn(Optional.of(testLocalIdentity));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.login(loginRequest, response));
        assertEquals("Invalid credentials", exception.getMessage());

        verify(userLocalIdentityRepository).incrementFailedLoginAttempts(testEmail);
        verify(userLocalIdentityRepository).lockAccount(eq(testEmail), any(LocalDateTime.class));
    }

    @Test
    void getCurrentUserShouldReturnUserResponseWhenAuthenticated() {
        // Given
        Cookie[] cookies = {new Cookie("access_token", testAccessToken)};
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(testAccessToken)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(testAccessToken)).thenReturn(testUserId);
        when(jwtService.isAccessTokenValid(testAccessToken, testUserId)).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // When
        UserResponse userResponse = authService.getCurrentUser(request);

        // Then
        assertNotNull(userResponse);
        assertEquals(testUserId, userResponse.getId());
        assertEquals(testEmail, userResponse.getEmail());
    }

    @Test
    void getCurrentUserShouldThrowExceptionWhenNotAuthenticated() {
        // Given
        when(request.getCookies()).thenReturn(null);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.getCurrentUser(request));
        assertEquals("User not authenticated", exception.getMessage());
    }

    @Test
    void getCurrentUserShouldThrowExceptionWhenUserNotFound() {
        // Given
        Cookie[] cookies = {new Cookie("access_token", testAccessToken)};
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(testAccessToken)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(testAccessToken)).thenReturn(testUserId);
        when(jwtService.isAccessTokenValid(testAccessToken, testUserId)).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.getCurrentUser(request));
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void logoutShouldClearTokensAndCookiesWhenAuthenticated() {
        // Given
        Cookie[] cookies = {new Cookie("access_token", testAccessToken)};
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(testAccessToken)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(testAccessToken)).thenReturn(testUserId);
        when(jwtService.isAccessTokenValid(testAccessToken, testUserId)).thenReturn(true);

        // When
        authService.logout(request, response);

        // Then
        verify(redisTokenService).deleteAllTokensForUser(testUserId, testAccessToken);
        verify(response, times(2)).addCookie(any(Cookie.class)); // Clear cookies
    }

    @Test
    void logoutShouldStillClearCookiesWhenNotAuthenticated() {
        // Given
        when(request.getCookies()).thenReturn(null);

        // When
        authService.logout(request, response);

        // Then
        verify(redisTokenService, never()).deleteAllTokensForUser(any(), any());
        verify(response, times(2)).addCookie(any(Cookie.class)); // Still clear cookies
    }

    @Test
    void refreshTokenShouldGenerateNewTokensWhenValidRefreshToken() {
        // Given
        Cookie[] cookies = {new Cookie("refresh_token", testRefreshToken)};
        when(request.getCookies()).thenReturn(cookies);
        when(jwtService.extractUserIdFromRefreshToken(testRefreshToken)).thenReturn(testUserId);
        when(jwtService.isRefreshTokenValid(testRefreshToken, testUserId)).thenReturn(true);
        when(redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken)).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(testUserId, testEmail)).thenReturn("new.access.token");
        when(jwtService.generateRefreshToken(testUserId, testEmail)).thenReturn("new.refresh.token");

        // When
        AuthResponse authResponse = authService.refreshToken(request, response);

        // Then
        assertNotNull(authResponse);
        assertEquals("Tokens refreshed successfully", authResponse.getMessage());
        assertNotNull(authResponse.getUser());

        verify(redisTokenService).storeAccessToken("new.access.token", testUserId);
        verify(redisTokenService).rotateRefreshToken(testUserId, "new.refresh.token");
        verify(response, times(2)).addCookie(any(Cookie.class)); // Set new cookies
    }

    @Test
    void refreshTokenShouldThrowExceptionWhenNoRefreshToken() {
        // Given
        when(request.getCookies()).thenReturn(null);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.refreshToken(request, response));
        assertEquals("Refresh token not found", exception.getMessage());
    }

    @Test
    void refreshTokenShouldThrowExceptionWhenInvalidRefreshToken() {
        // Given
        Cookie[] cookies = {new Cookie("refresh_token", testRefreshToken)};
        when(request.getCookies()).thenReturn(cookies);
        when(jwtService.extractUserIdFromRefreshToken(testRefreshToken)).thenReturn(testUserId);
        when(jwtService.isRefreshTokenValid(testRefreshToken, testUserId)).thenReturn(false);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.refreshToken(request, response));
        assertEquals("Invalid refresh token", exception.getMessage());
    }

    @Test
    void refreshTokenShouldThrowExceptionWhenTokenNotInRedis() {
        // Given
        Cookie[] cookies = {new Cookie("refresh_token", testRefreshToken)};
        when(request.getCookies()).thenReturn(cookies);
        when(jwtService.extractUserIdFromRefreshToken(testRefreshToken)).thenReturn(testUserId);
        when(jwtService.isRefreshTokenValid(testRefreshToken, testUserId)).thenReturn(true);
        when(redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken)).thenReturn(false);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.refreshToken(request, response));
        assertEquals("Refresh token not found or expired", exception.getMessage());
    }

    @Test
    void refreshTokenShouldThrowExceptionWhenUserInactive() {
        // Given
        Cookie[] cookies = {new Cookie("refresh_token", testRefreshToken)};
        testUser.setIsActive(false);

        when(request.getCookies()).thenReturn(cookies);
        when(jwtService.extractUserIdFromRefreshToken(testRefreshToken)).thenReturn(testUserId);
        when(jwtService.isRefreshTokenValid(testRefreshToken, testUserId)).thenReturn(true);
        when(redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken)).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.refreshToken(request, response));
        assertEquals("User account is inactive", exception.getMessage());
    }

    @Test
    void refreshTokenShouldThrowExceptionWhenUserNotFound() {
        // Given
        Cookie[] cookies = {new Cookie("refresh_token", testRefreshToken)};
        when(request.getCookies()).thenReturn(cookies);
        when(jwtService.extractUserIdFromRefreshToken(testRefreshToken)).thenReturn(testUserId);
        when(jwtService.isRefreshTokenValid(testRefreshToken, testUserId)).thenReturn(true);
        when(redisTokenService.isRefreshTokenValid(testUserId, testRefreshToken)).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.refreshToken(request, response));
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void isAuthenticatedShouldReturnTrueWhenValidToken() {
        // Given
        Cookie[] cookies = {new Cookie("access_token", testAccessToken)};
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(testAccessToken)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(testAccessToken)).thenReturn(testUserId);
        when(jwtService.isAccessTokenValid(testAccessToken, testUserId)).thenReturn(true);

        // When
        boolean result = authService.isAuthenticated(request);

        // Then
        assertTrue(result);
    }

    @Test
    void isAuthenticatedShouldReturnFalseWhenNoToken() {
        // Given
        when(request.getCookies()).thenReturn(null);

        // When
        boolean result = authService.isAuthenticated(request);

        // Then
        assertFalse(result);
    }

    @Test
    void isAuthenticatedShouldReturnFalseWhenTokenNotInRedis() {
        // Given
        Cookie[] cookies = {new Cookie("access_token", testAccessToken)};
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(testAccessToken)).thenReturn(false);

        // When
        boolean result = authService.isAuthenticated(request);

        // Then
        assertFalse(result);
    }

    @Test
    void isAuthenticatedShouldReturnFalseWhenJwtInvalid() {
        // Given
        Cookie[] cookies = {new Cookie("access_token", testAccessToken)};
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(testAccessToken)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(testAccessToken)).thenReturn(testUserId);
        when(jwtService.isAccessTokenValid(testAccessToken, testUserId)).thenReturn(false);

        // When
        boolean result = authService.isAuthenticated(request);

        // Then
        assertFalse(result);
    }

    @Test
    void isAuthenticatedShouldReturnFalseWhenJwtExtractionFails() {
        // Given
        Cookie[] cookies = {new Cookie("access_token", testAccessToken)};
        when(request.getCookies()).thenReturn(cookies);
        when(redisTokenService.isAccessTokenValid(testAccessToken)).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken(testAccessToken)).thenThrow(new RuntimeException("Invalid JWT"));

        // When
        boolean result = authService.isAuthenticated(request);

        // Then
        assertFalse(result);
    }
}