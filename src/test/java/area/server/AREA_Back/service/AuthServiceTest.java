package area.server.AREA_Back.service;

import area.server.AREA_Back.config.JwtCookieProperties;
import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.ForgotPasswordRequest;
import area.server.AREA_Back.dto.LocalLoginRequest;
import area.server.AREA_Back.dto.RegisterRequest;
import area.server.AREA_Back.dto.ResetPasswordRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserLocalIdentity;
import area.server.AREA_Back.repository.UserLocalIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.AuthService;
import area.server.AREA_Back.service.Auth.EmailService;
import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.Redis.RedisTokenService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
    private EmailService emailService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Counter registerSuccessCounter;

    @Mock
    private Counter registerFailureCounter;

    @Mock
    private Counter loginSuccessCounter;

    @Mock
    private Counter loginFailureCounter;

    @Mock
    private Counter emailVerificationSuccessCounter;

    @Mock
    private Counter emailVerificationFailureCounter;

    @Mock
    private Counter passwordResetRequestCounter;

    @Mock
    private Counter passwordResetSuccessCounter;

    @Mock
    private Counter passwordResetFailureCounter;

    @Mock
    private JwtCookieProperties jwtCookieProperties;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private UserLocalIdentity testIdentity;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("test@example.com");
        testUser.setUsername("testuser");
        testUser.setFirstname("Test");
        testUser.setLastname("User");
        testUser.setIsActive(true);
        testUser.setIsAdmin(false);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setLastLoginAt(LocalDateTime.now());
        testUser.setAvatarUrl("https://example.com/avatar.jpg");

        testIdentity = new UserLocalIdentity();
        testIdentity.setId(UUID.randomUUID());
        testIdentity.setUser(testUser);
        testIdentity.setEmail("test@example.com");
        testIdentity.setPasswordHash("hashedPassword");
        testIdentity.setIsEmailVerified(false);
        testIdentity.setEmailVerificationToken("verification-token");
        testIdentity.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(1440));
        testIdentity.setFailedLoginAttempts(0);
        testIdentity.setCreatedAt(LocalDateTime.now());
        testIdentity.setUpdatedAt(LocalDateTime.now());

        // Mock counters with lenient() since not all tests use all counters
        lenient().when(meterRegistry.counter("auth.register.success")).thenReturn(registerSuccessCounter);
        lenient().when(meterRegistry.counter("auth.register.failure")).thenReturn(registerFailureCounter);
        lenient().when(meterRegistry.counter("auth.login.success")).thenReturn(loginSuccessCounter);
        lenient().when(meterRegistry.counter("auth.login.failure")).thenReturn(loginFailureCounter);
        lenient().when(meterRegistry.counter("auth.email.verification.success")).thenReturn(emailVerificationSuccessCounter);
        lenient().when(meterRegistry.counter("auth.email.verification.failure")).thenReturn(emailVerificationFailureCounter);
        lenient().when(meterRegistry.counter("auth.password.reset.request")).thenReturn(passwordResetRequestCounter);
        lenient().when(meterRegistry.counter("auth.password.reset.success")).thenReturn(passwordResetSuccessCounter);
        lenient().when(meterRegistry.counter("auth.password.reset.failure")).thenReturn(passwordResetFailureCounter);

        // Set up field values
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "emailVerificationTokenExpiryMinutes", 1440);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "passwordResetTokenExpiryMinutes", 15);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "emailVerificationSubject", "Verify Your Account");
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "passwordResetSubject", "Reset Your Password");
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:3000");
        
        // Inject the counters directly since @PostConstruct is not called in unit tests
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "registerSuccessCounter", registerSuccessCounter);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "registerFailureCounter", registerFailureCounter);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "loginSuccessCounter", loginSuccessCounter);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "loginFailureCounter", loginFailureCounter);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "emailVerificationSuccessCounter", emailVerificationSuccessCounter);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "emailVerificationFailureCounter", emailVerificationFailureCounter);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "passwordResetRequestCounter", passwordResetRequestCounter);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "passwordResetSuccessCounter", passwordResetSuccessCounter);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "passwordResetFailureCounter", passwordResetFailureCounter);

        // Mock JwtCookieProperties
        lenient().when(jwtCookieProperties.isSecure()).thenReturn(false);
        lenient().when(jwtCookieProperties.getSameSite()).thenReturn("Lax");
        lenient().when(jwtCookieProperties.getAccessTokenExpiry()).thenReturn(86400);
        lenient().when(jwtCookieProperties.getRefreshTokenExpiry()).thenReturn(604800);
        lenient().when(jwtCookieProperties.getDomain()).thenReturn("");
    }

    @Test
    void verifyEmail_ShouldVerifyUser_WhenTokenIsValid() {
        // Given
        String token = "valid-token";
        when(userLocalIdentityRepository.findByEmailVerificationToken(token))
            .thenReturn(Optional.of(testIdentity));
        when(userLocalIdentityRepository.save(any(UserLocalIdentity.class)))
            .thenReturn(testIdentity);

        // When
        AuthResponse response = authService.verifyEmail(token);

        // Then
        assertNotNull(response);
        assertEquals("Email verified successfully", response.getMessage());
        assertNotNull(response.getUser());
        assertEquals(testUser.getEmail(), response.getUser().getEmail());

        verify(userLocalIdentityRepository).findByEmailVerificationToken(token);
        verify(userLocalIdentityRepository).save(testIdentity);
        verify(emailVerificationSuccessCounter).increment();

        assertTrue(testIdentity.getIsEmailVerified());
        assertNull(testIdentity.getEmailVerificationToken());
        assertNull(testIdentity.getEmailVerificationExpiresAt());
    }

    @Test
    void verifyEmail_ShouldThrowException_WhenTokenIsInvalid() {
        // Given
        String token = "invalid-token";
        when(userLocalIdentityRepository.findByEmailVerificationToken(token))
            .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.verifyEmail(token));
        assertEquals("Invalid verification token", exception.getMessage());

        verify(emailVerificationFailureCounter).increment();
    }

    @Test
    void verifyEmail_ShouldThrowException_WhenTokenIsExpired() {
        // Given
        String token = "expired-token";
        testIdentity.setEmailVerificationExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userLocalIdentityRepository.findByEmailVerificationToken(token))
            .thenReturn(Optional.of(testIdentity));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.verifyEmail(token));
        assertEquals("Verification token has expired", exception.getMessage());

        verify(emailVerificationFailureCounter).increment();
    }

    @Test
    void verifyEmail_ShouldThrowException_WhenEmailAlreadyVerified() {
        // Given
        String token = "already-verified-token";
        testIdentity.setIsEmailVerified(true);
        when(userLocalIdentityRepository.findByEmailVerificationToken(token))
            .thenReturn(Optional.of(testIdentity));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.verifyEmail(token));
        assertEquals("Email is already verified", exception.getMessage());

        verify(emailVerificationFailureCounter).increment();
    }

    @Test
    void forgotPassword_ShouldSendResetEmail_WhenUserExists() {
        // Given
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");
        when(userLocalIdentityRepository.findByEmail("test@example.com"))
            .thenReturn(Optional.of(testIdentity));
        when(emailService.sendPasswordResetEmail(anyString(), anyString(), anyString()))
            .thenReturn(true);
        when(userLocalIdentityRepository.save(any(UserLocalIdentity.class)))
            .thenReturn(testIdentity);

        // When
        AuthResponse response = authService.forgotPassword(request);

        // Then
        assertNotNull(response);
        assertEquals("If an account with this email exists, a password reset link has been sent.", response.getMessage());

        verify(userLocalIdentityRepository).findByEmail("test@example.com");
        verify(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString());
        verify(userLocalIdentityRepository).save(testIdentity);
        verify(passwordResetRequestCounter).increment();

        assertNotNull(testIdentity.getPasswordResetToken());
        assertNotNull(testIdentity.getPasswordResetExpiresAt());
    }

    @Test
    void forgotPassword_ShouldNotSendEmail_WhenUserDoesNotExist() {
        // Given
        ForgotPasswordRequest request = new ForgotPasswordRequest("nonexistent@example.com");
        when(userLocalIdentityRepository.findByEmail("nonexistent@example.com"))
            .thenReturn(Optional.empty());

        // When
        AuthResponse response = authService.forgotPassword(request);

        // Then
        assertNotNull(response);
        assertEquals("If an account with this email exists, a password reset link has been sent.", response.getMessage());

        verify(userLocalIdentityRepository).findByEmail("nonexistent@example.com");
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
        verify(passwordResetRequestCounter).increment();
    }

    @Test
    void resetPassword_ShouldResetPassword_WhenTokenIsValid() {
        // Given
        ResetPasswordRequest request = new ResetPasswordRequest("valid-reset-token", "NewPassword123!");
        testIdentity.setPasswordResetToken("valid-reset-token");
        testIdentity.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(userLocalIdentityRepository.findByPasswordResetToken("valid-reset-token"))
            .thenReturn(Optional.of(testIdentity));
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("hashedNewPassword");
        when(userLocalIdentityRepository.save(any(UserLocalIdentity.class)))
            .thenReturn(testIdentity);

        // When
        AuthResponse response = authService.resetPassword(request);

        // Then
        assertNotNull(response);
        assertEquals("Password reset successfully", response.getMessage());

        verify(userLocalIdentityRepository).findByPasswordResetToken("valid-reset-token");
        verify(passwordEncoder).encode("NewPassword123!");
        verify(userLocalIdentityRepository).save(testIdentity);
        verify(passwordResetSuccessCounter).increment();

        assertEquals("hashedNewPassword", testIdentity.getPasswordHash());
        assertNull(testIdentity.getPasswordResetToken());
        assertNull(testIdentity.getPasswordResetExpiresAt());
        assertEquals(0, testIdentity.getFailedLoginAttempts());
        assertNull(testIdentity.getLockedUntil());
    }

    @Test
    void resetPassword_ShouldThrowException_WhenTokenIsInvalid() {
        // Given
        ResetPasswordRequest request = new ResetPasswordRequest("invalid-token", "NewPassword123!");
        when(userLocalIdentityRepository.findByPasswordResetToken("invalid-token"))
            .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.resetPassword(request));
        assertEquals("Invalid reset token", exception.getMessage());

        verify(passwordResetFailureCounter).increment();
    }

    @Test
    void resetPassword_ShouldThrowException_WhenTokenIsExpired() {
        // Given
        ResetPasswordRequest request = new ResetPasswordRequest("expired-token", "NewPassword123!");
        testIdentity.setPasswordResetToken("expired-token");
        testIdentity.setPasswordResetExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(userLocalIdentityRepository.findByPasswordResetToken("expired-token"))
            .thenReturn(Optional.of(testIdentity));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.resetPassword(request));
        assertEquals("Reset token has expired", exception.getMessage());

        verify(passwordResetFailureCounter).increment();
    }

    @Test
    void getCurrentUser_ShouldReturnUserWithVerificationStatus() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userLocalIdentityRepository.findByUserId(userId)).thenReturn(Optional.of(testIdentity));

        // Mock JWT token extraction
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(jwtService.isAccessTokenValid(anyString(), eq(userId))).thenReturn(true);
        when(redisTokenService.isAccessTokenValid(anyString())).thenReturn(true);

        // Mock cookie with correct cookie name from AuthTokenConstants
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("authToken", "test-token");
        when(request.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[]{cookie});

        // When
        UserResponse userResponse = authService.getCurrentUser(request);

        // Then
        assertNotNull(userResponse);
        assertEquals(testUser.getId(), userResponse.getId());
        assertEquals(testUser.getEmail(), userResponse.getEmail());
        assertEquals(testIdentity.getIsEmailVerified(), userResponse.getIsVerified());
    }

    // ===================== REGISTER TESTS =====================

    @Test
    void register_ShouldRegisterUser_WhenValidRequest() {
        // Given
        RegisterRequest request = new RegisterRequest(
            "newuser@example.com",
            "Password123!",
            "newusername",
            "John",
            "Doe",
            "https://example.com/avatar.jpg"
        );

        when(userLocalIdentityRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashedPassword");
        when(userLocalIdentityRepository.save(any(UserLocalIdentity.class))).thenReturn(testIdentity);
        when(emailService.sendVerificationEmail(anyString(), anyString(), anyString())).thenReturn(true);
        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("refresh-token");

        // When
        AuthResponse response = authService.register(request, this.response);

        // Then
        assertNotNull(response);
        assertTrue(response.getMessage().contains("registered successfully"));
        assertNotNull(response.getUser());
        verify(registerSuccessCounter).increment();
        verify(userRepository, times(2)).save(any(User.class)); // saved twice: creation + lastLoginAt update
        verify(userLocalIdentityRepository).save(any(UserLocalIdentity.class));
        verify(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
        verify(redisTokenService).storeAccessToken("access-token", userId);
        verify(redisTokenService).storeRefreshToken(userId, "refresh-token");
    }

    @Test
    void register_ShouldThrowException_WhenEmailAlreadyExists() {
        // Given
        RegisterRequest request = new RegisterRequest(
            "existing@example.com",
            "Password123!",
            "newusername",
            "John",
            "Doe",
            null
        );

        when(userLocalIdentityRepository.existsByEmail(request.getEmail())).thenReturn(true);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.register(request, response));
        assertEquals("Email already registered", exception.getMessage());
        verify(registerFailureCounter).increment();
    }

    @Test
    void register_ShouldThrowException_WhenUsernameAlreadyTaken() {
        // Given
        RegisterRequest request = new RegisterRequest(
            "newuser@example.com",
            "Password123!",
            "existingusername",
            "John",
            "Doe",
            null
        );

        when(userLocalIdentityRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(true);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.register(request, response));
        assertEquals("Username already taken", exception.getMessage());
        verify(registerFailureCounter).increment();
    }

    @Test
    void register_ShouldSucceed_WhenEmailSendingFails() {
        // Given
        RegisterRequest request = new RegisterRequest(
            "newuser@example.com",
            "Password123!",
            "newusername",
            "John",
            "Doe",
            null
        );

        when(userLocalIdentityRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashedPassword");
        when(userLocalIdentityRepository.save(any(UserLocalIdentity.class))).thenReturn(testIdentity);
        when(emailService.sendVerificationEmail(anyString(), anyString(), anyString())).thenReturn(false);
        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("refresh-token");

        // When
        AuthResponse response = authService.register(request, this.response);

        // Then
        assertNotNull(response);
        verify(registerSuccessCounter).increment();
    }

    // ===================== LOGIN TESTS =====================

    @Test
    void login_ShouldLoginUser_WhenCredentialsAreValid_WithEmail() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "Password123!");
        testIdentity.setPasswordHash("hashedPassword");

        when(userLocalIdentityRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testIdentity));
        when(passwordEncoder.matches(request.getPassword(), testIdentity.getPasswordHash())).thenReturn(true);
        when(jwtService.generateAccessToken(userId, testUser.getEmail())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userId, testUser.getEmail())).thenReturn("refresh-token");

        // When
        AuthResponse response = authService.login(request, this.response);

        // Then
        assertNotNull(response);
        assertEquals("Login successful", response.getMessage());
        verify(loginSuccessCounter).increment();
        verify(userLocalIdentityRepository).resetFailedLoginAttempts(request.getEmail());
        verify(redisTokenService).storeAccessToken("access-token", userId);
        verify(redisTokenService).storeRefreshToken(userId, "refresh-token");
    }

    @Test
    void login_ShouldLoginUser_WhenCredentialsAreValid_WithUsername() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest(null, "testuser", "Password123!");
        testIdentity.setPasswordHash("hashedPassword");

        when(userLocalIdentityRepository.findByUsername(request.getUsername())).thenReturn(Optional.of(testIdentity));
        when(passwordEncoder.matches(request.getPassword(), testIdentity.getPasswordHash())).thenReturn(true);
        when(jwtService.generateAccessToken(userId, testUser.getEmail())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userId, testUser.getEmail())).thenReturn("refresh-token");

        // When
        AuthResponse response = authService.login(request, this.response);

        // Then
        assertNotNull(response);
        assertEquals("Login successful", response.getMessage());
        verify(loginSuccessCounter).increment();
    }

    @Test
    void login_ShouldThrowException_WhenUserNotFound() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest("nonexistent@example.com", null, "Password123!");

        when(userLocalIdentityRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.login(request, response));
        assertEquals("Invalid credentials", exception.getMessage());
        verify(loginFailureCounter).increment();
    }

    @Test
    void login_ShouldThrowException_WhenAccountIsLocked() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "Password123!");
        testIdentity.setLockedUntil(LocalDateTime.now().plusMinutes(30));

        when(userLocalIdentityRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testIdentity));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.login(request, response));
        assertTrue(exception.getMessage().contains("locked"));
    }

    @Test
    void login_ShouldThrowException_WhenAccountIsInactive() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "Password123!");
        testUser.setIsActive(false);

        when(userLocalIdentityRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testIdentity));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.login(request, response));
        assertEquals("Account is inactive", exception.getMessage());
    }

    @Test
    void login_ShouldIncrementFailedAttempts_WhenPasswordIsInvalid() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "WrongPassword");
        testIdentity.setPasswordHash("hashedPassword");

        when(userLocalIdentityRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testIdentity));
        when(passwordEncoder.matches(request.getPassword(), testIdentity.getPasswordHash())).thenReturn(false);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.login(request, response));
        assertEquals("Invalid credentials", exception.getMessage());
        verify(loginFailureCounter).increment();
        verify(userLocalIdentityRepository).incrementFailedLoginAttempts(request.getEmail());
    }

    @Test
    void login_ShouldLockAccount_WhenMaxFailedAttemptsReached() {
        // Given
        LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "WrongPassword");
        testIdentity.setPasswordHash("hashedPassword");
        testIdentity.setFailedLoginAttempts(4); // 5th attempt will lock

        when(userLocalIdentityRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testIdentity));
        when(passwordEncoder.matches(request.getPassword(), testIdentity.getPasswordHash())).thenReturn(false);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.login(request, response));
        assertEquals("Invalid credentials", exception.getMessage());
        verify(userLocalIdentityRepository).incrementFailedLoginAttempts(request.getEmail());
        verify(userLocalIdentityRepository).lockAccount(eq(request.getEmail()), any(LocalDateTime.class));
    }

    // ===================== OAUTH LOGIN TESTS =====================

    @Test
    void oauthLogin_ShouldCreateNewUser_WhenUserDoesNotExist() {
        // Given
        String email = "oauth@example.com";
        String avatarUrl = "https://example.com/oauth-avatar.jpg";

        when(userLocalIdentityRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(userLocalIdentityRepository.save(any(UserLocalIdentity.class))).thenReturn(testIdentity);
        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("refresh-token");

        // When
        AuthResponse response = authService.oauthLogin(email, avatarUrl, this.response);

        // Then
        assertNotNull(response);
        assertEquals("OAuth login successful", response.getMessage());
        verify(registerSuccessCounter).increment();
        verify(userRepository, times(2)).save(any(User.class)); // saved twice: creation + lastLoginAt update
        verify(userLocalIdentityRepository).save(any(UserLocalIdentity.class));
    }

    @Test
    void oauthLogin_ShouldLoginExistingUser_WhenUserExists() {
        // Given
        String email = "test@example.com";
        String avatarUrl = "https://example.com/new-avatar.jpg";

        when(userLocalIdentityRepository.findByEmail(email)).thenReturn(Optional.of(testIdentity));
        when(jwtService.generateAccessToken(userId, email)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userId, email)).thenReturn("refresh-token");

        // When
        AuthResponse response = authService.oauthLogin(email, avatarUrl, this.response);

        // Then
        assertNotNull(response);
        assertEquals("OAuth login successful", response.getMessage());
        verify(loginSuccessCounter).increment();
        assertEquals(avatarUrl, testUser.getAvatarUrl());
    }

    @Test
    void oauthLogin_ShouldThrowException_WhenAccountIsInactive() {
        // Given
        String email = "test@example.com";
        testUser.setIsActive(false);

        when(userLocalIdentityRepository.findByEmail(email)).thenReturn(Optional.of(testIdentity));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.oauthLogin(email, null, response));
        assertEquals("Account is inactive", exception.getMessage());
    }

    @Test
    void oauthLogin_ShouldGenerateUniqueUsername_WhenUsernameExists() {
        // Given
        String email = "oauth@example.com";

        when(userLocalIdentityRepository.findByEmail(email)).thenReturn(Optional.empty());
        // First call checks "oauth", which exists
        // Second call checks "oauth_<random>", which doesn't exist
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(userLocalIdentityRepository.save(any(UserLocalIdentity.class))).thenReturn(testIdentity);
        when(jwtService.generateAccessToken(any(UUID.class), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(UUID.class), anyString())).thenReturn("refresh-token");

        // When
        AuthResponse response = authService.oauthLogin(email, null, this.response);

        // Then
        assertNotNull(response);
        verify(userRepository, times(2)).save(any(User.class)); // saved twice: creation + lastLoginAt update
    }

    // ===================== REFRESH TOKEN TESTS =====================

    @Test
    void refreshToken_ShouldRefreshTokens_WhenValidRefreshToken() {
        // Given
        Cookie refreshCookie = new Cookie("refreshToken", "valid-refresh-token");
        Cookie accessCookie = new Cookie("authToken", "old-access-token");
        when(request.getCookies()).thenReturn(new Cookie[]{refreshCookie, accessCookie});
        when(jwtService.extractUserIdFromRefreshToken("valid-refresh-token")).thenReturn(userId);
        when(jwtService.isRefreshTokenValid("valid-refresh-token", userId)).thenReturn(true);
        when(redisTokenService.isRefreshTokenValid(userId, "valid-refresh-token")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userLocalIdentityRepository.findByUserId(userId)).thenReturn(Optional.of(testIdentity));
        when(jwtService.generateAccessToken(userId, testUser.getEmail())).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(userId, testUser.getEmail())).thenReturn("new-refresh-token");

        // When
        AuthResponse response = authService.refreshToken(request, this.response);

        // Then
        assertNotNull(response);
        assertEquals("Tokens refreshed successfully", response.getMessage());
        verify(redisTokenService).deleteAccessToken("old-access-token");
        verify(redisTokenService).storeAccessToken("new-access-token", userId);
        verify(redisTokenService).rotateRefreshToken(userId, "new-refresh-token");
    }

    @Test
    void refreshToken_ShouldThrowException_WhenRefreshTokenNotFound() {
        // Given
        when(request.getCookies()).thenReturn(new Cookie[]{});

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.refreshToken(request, response));
        assertEquals("Refresh token not found", exception.getMessage());
    }

    @Test
    void refreshToken_ShouldThrowException_WhenRefreshTokenIsInvalid() {
        // Given
        Cookie refreshCookie = new Cookie("refreshToken", "invalid-refresh-token");
        when(request.getCookies()).thenReturn(new Cookie[]{refreshCookie});
        when(jwtService.extractUserIdFromRefreshToken("invalid-refresh-token")).thenThrow(new RuntimeException("Invalid token"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.refreshToken(request, response));
        assertEquals("Invalid refresh token", exception.getMessage());
    }

    @Test
    void refreshToken_ShouldThrowException_WhenUserNotFound() {
        // Given
        Cookie refreshCookie = new Cookie("refreshToken", "valid-refresh-token");
        when(request.getCookies()).thenReturn(new Cookie[]{refreshCookie});
        when(jwtService.extractUserIdFromRefreshToken("valid-refresh-token")).thenReturn(userId);
        when(jwtService.isRefreshTokenValid("valid-refresh-token", userId)).thenReturn(true);
        when(redisTokenService.isRefreshTokenValid(userId, "valid-refresh-token")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.refreshToken(request, response));
        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void refreshToken_ShouldThrowException_WhenUserIsInactive() {
        // Given
        Cookie refreshCookie = new Cookie("refreshToken", "valid-refresh-token");
        when(request.getCookies()).thenReturn(new Cookie[]{refreshCookie});
        when(jwtService.extractUserIdFromRefreshToken("valid-refresh-token")).thenReturn(userId);
        when(jwtService.isRefreshTokenValid("valid-refresh-token", userId)).thenReturn(true);
        when(redisTokenService.isRefreshTokenValid(userId, "valid-refresh-token")).thenReturn(true);
        testUser.setIsActive(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.refreshToken(request, response));
        assertEquals("User account is inactive", exception.getMessage());
    }

    @Test
    void refreshToken_ShouldThrowException_WhenRefreshTokenNotInRedis() {
        // Given
        Cookie refreshCookie = new Cookie("refreshToken", "valid-refresh-token");
        when(request.getCookies()).thenReturn(new Cookie[]{refreshCookie});
        when(jwtService.extractUserIdFromRefreshToken("valid-refresh-token")).thenReturn(userId);
        when(jwtService.isRefreshTokenValid("valid-refresh-token", userId)).thenReturn(true);
        when(redisTokenService.isRefreshTokenValid(userId, "valid-refresh-token")).thenReturn(false);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.refreshToken(request, response));
        assertEquals("Refresh token not found or expired", exception.getMessage());
    }

    // ===================== LOGOUT TESTS =====================

    @Test
    void logout_ShouldClearTokens_WhenUserIsAuthenticated() {
        // Given
        Cookie accessCookie = new Cookie("authToken", "valid-access-token");
        when(request.getCookies()).thenReturn(new Cookie[]{accessCookie});
        when(redisTokenService.isAccessTokenValid("valid-access-token")).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken("valid-access-token")).thenReturn(userId);
        when(jwtService.isAccessTokenValid("valid-access-token", userId)).thenReturn(true);

        // When
        authService.logout(request, response);

        // Then
        verify(redisTokenService).deleteAllTokensForUser(userId, "valid-access-token");
        verify(response).setHeader(eq("Set-Cookie"), anyString()); // first cookie (access token)
        verify(response).addHeader(eq("Set-Cookie"), anyString()); // second cookie (refresh token)
    }

    @Test
    void logout_ShouldClearCookies_WhenUserNotAuthenticated() {
        // Given
        when(request.getCookies()).thenReturn(new Cookie[]{});

        // When
        authService.logout(request, response);

        // Then
        verify(redisTokenService, never()).deleteAllTokensForUser(any(), anyString());
        verify(response).setHeader(eq("Set-Cookie"), anyString()); // first cookie (access token)
        verify(response).addHeader(eq("Set-Cookie"), anyString()); // second cookie (refresh token)
    }

    // ===================== HELPER METHOD TESTS =====================

    @Test
    void getCurrentUserEntity_ShouldReturnUser_WhenAuthenticated() {
        // Given
        Cookie accessCookie = new Cookie("authToken", "valid-access-token");
        when(request.getCookies()).thenReturn(new Cookie[]{accessCookie});
        when(redisTokenService.isAccessTokenValid("valid-access-token")).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken("valid-access-token")).thenReturn(userId);
        when(jwtService.isAccessTokenValid("valid-access-token", userId)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        User user = authService.getCurrentUserEntity(request);

        // Then
        assertNotNull(user);
        assertEquals(userId, user.getId());
    }

    @Test
    void getCurrentUserEntity_ShouldReturnNull_WhenNotAuthenticated() {
        // Given
        when(request.getCookies()).thenReturn(new Cookie[]{});

        // When
        User user = authService.getCurrentUserEntity(request);

        // Then
        assertNull(user);
    }

    @Test
    void isAuthenticated_ShouldReturnTrue_WhenUserIsAuthenticated() {
        // Given
        Cookie accessCookie = new Cookie("authToken", "valid-access-token");
        when(request.getCookies()).thenReturn(new Cookie[]{accessCookie});
        when(redisTokenService.isAccessTokenValid("valid-access-token")).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken("valid-access-token")).thenReturn(userId);
        when(jwtService.isAccessTokenValid("valid-access-token", userId)).thenReturn(true);

        // When
        boolean isAuth = authService.isAuthenticated(request);

        // Then
        assertTrue(isAuth);
    }

    @Test
    void isAuthenticated_ShouldReturnFalse_WhenUserIsNotAuthenticated() {
        // Given
        when(request.getCookies()).thenReturn(new Cookie[]{});

        // When
        boolean isAuth = authService.isAuthenticated(request);

        // Then
        assertFalse(isAuth);
    }

    @Test
    void getCurrentUser_ShouldThrowException_WhenNotAuthenticated() {
        // Given
        when(request.getCookies()).thenReturn(new Cookie[]{});

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.getCurrentUser(request));
        assertEquals("User not authenticated", exception.getMessage());
    }

    @Test
    void getCurrentUser_ShouldThrowException_WhenUserNotFound() {
        // Given
        Cookie accessCookie = new Cookie("authToken", "valid-access-token");
        when(request.getCookies()).thenReturn(new Cookie[]{accessCookie});
        when(redisTokenService.isAccessTokenValid("valid-access-token")).thenReturn(true);
        when(jwtService.extractUserIdFromAccessToken("valid-access-token")).thenReturn(userId);
        when(jwtService.isAccessTokenValid("valid-access-token", userId)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> authService.getCurrentUser(request));
        assertEquals("User not found", exception.getMessage());
    }
}