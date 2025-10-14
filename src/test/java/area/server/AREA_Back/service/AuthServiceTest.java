package area.server.AREA_Back.service;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.ForgotPasswordRequest;
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
}