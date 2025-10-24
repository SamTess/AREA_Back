package area.server.AREA_Back.service.Auth;

import area.server.AREA_Back.config.JwtCookieProperties;
import area.server.AREA_Back.constants.AuthTokenConstants;
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
import area.server.AREA_Back.service.Redis.RedisTokenService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserLocalIdentityRepository userLocalIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisTokenService redisTokenService;
    private final JwtCookieProperties jwtCookieProperties;
    private final EmailService emailService;
    private final MeterRegistry meterRegistry;

    @Value("${app.email.verification.token-expiry-minutes}")
    private int emailVerificationTokenExpiryMinutes;

    @Value("${app.email.reset.token-expiry-minutes}")
    private int passwordResetTokenExpiryMinutes;

    @Value("${app.email.verification.subject}")
    private String emailVerificationSubject;

    @Value("${app.email.reset.subject}")
    private String passwordResetSubject;

    @Value("${app.email.frontend-url}")
    private String frontendUrl;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int ACCOUNT_LOCK_DURATION_MINUTES = 30;

    private Counter registerSuccessCounter;
    private Counter registerFailureCounter;
    private Counter loginSuccessCounter;
    private Counter loginFailureCounter;
    private Counter emailVerificationSuccessCounter;
    private Counter emailVerificationFailureCounter;
    private Counter passwordResetRequestCounter;
    private Counter passwordResetSuccessCounter;
    private Counter passwordResetFailureCounter;

    @PostConstruct
    private void init() {

        registerSuccessCounter = Counter.builder("auth.register.success")
            .description("Successful user registrations")
            .register(meterRegistry);

        registerFailureCounter = Counter.builder("auth.register.failure")
            .description("Failed user registrations")
            .register(meterRegistry);

        loginSuccessCounter = Counter.builder("auth.login.success")
            .description("Successful user logins")
            .register(meterRegistry);

        loginFailureCounter = Counter.builder("auth.login.failure")
            .description("Failed user logins")
            .register(meterRegistry);

        emailVerificationSuccessCounter = Counter.builder("auth.email.verification.success")
            .description("Successful email verifications")
            .register(meterRegistry);

        emailVerificationFailureCounter = Counter.builder("auth.email.verification.failure")
            .description("Failed email verifications")
            .register(meterRegistry);

        passwordResetRequestCounter = Counter.builder("auth.password.reset.request")
            .description("Password reset requests")
            .register(meterRegistry);

        passwordResetSuccessCounter = Counter.builder("auth.password.reset.success")
            .description("Successful password resets")
            .register(meterRegistry);

        passwordResetFailureCounter = Counter.builder("auth.password.reset.failure")
            .description("Failed password resets")
            .register(meterRegistry);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletResponse response) {
        log.info("Attempting to register user with email: { }", request.getEmail());

        if (userLocalIdentityRepository.existsByEmail(request.getEmail())) {
            registerFailureCounter.increment();
            throw new RuntimeException("Email already registered");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            registerFailureCounter.increment();
            throw new RuntimeException("Username already taken");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setUsername(request.getUsername());
        user.setFirstname(request.getFirstName());
        user.setLastname(request.getLastName());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setIsActive(true);
        user.setIsAdmin(false);
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        log.debug("Created user with ID: { }", savedUser.getId());

        String verificationToken = UUID.randomUUID().toString();
        LocalDateTime tokenExpiry = LocalDateTime.now().plusMinutes(emailVerificationTokenExpiryMinutes);

        UserLocalIdentity localIdentity = new UserLocalIdentity();
        localIdentity.setUser(savedUser);
        localIdentity.setEmail(request.getEmail());
        localIdentity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        localIdentity.setIsEmailVerified(false);
        localIdentity.setEmailVerificationToken(verificationToken);
        localIdentity.setEmailVerificationExpiresAt(tokenExpiry);
        localIdentity.setFailedLoginAttempts(0);
        localIdentity.setCreatedAt(LocalDateTime.now());
        localIdentity.setUpdatedAt(LocalDateTime.now());

        userLocalIdentityRepository.save(localIdentity);
        log.debug("Created local identity for user: { }", savedUser.getId());

        try {
            String verificationUrl = frontendUrl + "/verify-email?token=" + verificationToken;
            boolean emailSent = emailService.sendVerificationEmail(
                request.getEmail(),
                emailVerificationSubject,
                verificationUrl
            );

            if (!emailSent) {
                log.warn("Failed to send verification email to: {}", request.getEmail());
            }
        } catch (Exception e) {
            log.error("Error sending verification email to: {}", request.getEmail(), e);
        }

        String accessToken = jwtService.generateAccessToken(savedUser.getId(), savedUser.getEmail());
        String refreshToken = jwtService.generateRefreshToken(savedUser.getId(), savedUser.getEmail());

        redisTokenService.storeAccessToken(accessToken, savedUser.getId());
        redisTokenService.storeRefreshToken(savedUser.getId(), refreshToken);

        setTokenCookies(response, accessToken, refreshToken);

        savedUser.setLastLoginAt(LocalDateTime.now());
        userRepository.save(savedUser);

        log.info("Successfully registered user: { }", savedUser.getEmail());
        registerSuccessCounter.increment();

        return new AuthResponse(
            "User registered successfully. Please check your email to verify your account.",
            mapToUserResponse(savedUser, false)
        );
    }

    @Transactional
    public AuthResponse login(LocalLoginRequest request, HttpServletResponse response) {
        String identifier = request.getEmail() != null ? request.getEmail() : request.getUsername();
        log.info("Attempting to login user with identifier: { }", identifier);

        Optional<UserLocalIdentity> localIdentityOpt = Optional.empty();

        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            localIdentityOpt = userLocalIdentityRepository.findByEmail(request.getEmail());
        } else if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            localIdentityOpt = userLocalIdentityRepository.findByUsername(request.getUsername());
        }

        if (localIdentityOpt.isEmpty()) {
            log.warn("Login attempt with non-existent identifier: { }", identifier);
            loginFailureCounter.increment();
            throw new RuntimeException("Invalid credentials");
        }

        UserLocalIdentity localIdentity = localIdentityOpt.get();
        User user = localIdentity.getUser();

        if (localIdentity.isAccountLocked()) {
            log.warn("Login attempt on locked account: { }", request.getEmail());
            throw new RuntimeException("Account is temporarily locked due to failed login attempts");
        }

        if (!user.getIsActive()) {
            log.warn("Login attempt on inactive account: { }", request.getEmail());
            throw new RuntimeException("Account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), localIdentity.getPasswordHash())) {
            log.warn("Failed login attempt for email: { }", request.getEmail());
            loginFailureCounter.increment();

            userLocalIdentityRepository.incrementFailedLoginAttempts(request.getEmail());

            if (localIdentity.getFailedLoginAttempts() + 1 >= MAX_FAILED_ATTEMPTS) {
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(ACCOUNT_LOCK_DURATION_MINUTES);
                userLocalIdentityRepository.lockAccount(request.getEmail(), lockUntil);
                log.warn("Account locked due to failed attempts: { }", request.getEmail());
            }

            throw new RuntimeException("Invalid credentials");
        }

        userLocalIdentityRepository.resetFailedLoginAttempts(request.getEmail());

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        redisTokenService.storeAccessToken(accessToken, user.getId());
        redisTokenService.storeRefreshToken(user.getId(), refreshToken);

        setTokenCookies(response, accessToken, refreshToken);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Successfully logged in user: { }", user.getEmail());
        loginSuccessCounter.increment();

        return new AuthResponse(
            "Login successful",
            mapToUserResponse(user, localIdentity.getIsEmailVerified())
        );
    }

    @Transactional
    public AuthResponse oauthLogin(String email, String avatarUrl, HttpServletResponse response) {
        log.info("Attempting OAuth login for email: {}", email);

        Optional<UserLocalIdentity> localIdentityOpt = userLocalIdentityRepository.findByEmail(email);
        User user;
        UserLocalIdentity localIdentity;

        if (localIdentityOpt.isEmpty()) {
            user = new User();
            user.setEmail(email);
            user.setUsername(generateUniqueUsername(null, email));
            user.setAvatarUrl(avatarUrl);
            user.setIsActive(true);
            user.setIsAdmin(false);
            user.setCreatedAt(LocalDateTime.now());
            user = userRepository.save(user);

            localIdentity = new UserLocalIdentity();
            localIdentity.setUser(user);
            localIdentity.setEmail(email);
            localIdentity.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            localIdentity.setIsEmailVerified(true);
            localIdentity.setFailedLoginAttempts(0);
            localIdentity.setCreatedAt(LocalDateTime.now());
            localIdentity.setUpdatedAt(LocalDateTime.now());
            userLocalIdentityRepository.save(localIdentity);

            log.info("Registered new OAuth user: {}", email);
            registerSuccessCounter.increment();
        } else {
            localIdentity = localIdentityOpt.get();
            user = localIdentity.getUser();

            if (!user.getIsActive()) {
                throw new RuntimeException("Account is inactive");
            }

            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                user.setAvatarUrl(avatarUrl);
            }

            if (user.getUsername() == null || user.getUsername().isEmpty()) {
                user.setUsername(generateUniqueUsername(null, email));
            }

            log.info("Logged in existing OAuth user: {}", email);
            loginSuccessCounter.increment();
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());
        redisTokenService.storeAccessToken(accessToken, user.getId());
        redisTokenService.storeRefreshToken(user.getId(), refreshToken);
        setTokenCookies(response, accessToken, refreshToken);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return new AuthResponse(
            "OAuth login successful",
            mapToUserResponse(user, localIdentity.getIsEmailVerified())
        );
    }

    public UserResponse getCurrentUser(HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        if (userId == null) {
            throw new RuntimeException("User not authenticated");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<UserLocalIdentity> localIdentityOpt = userLocalIdentityRepository.findByUserId(userId);
        Boolean isVerified = localIdentityOpt.map(UserLocalIdentity::getIsEmailVerified).orElse(false);

        UserResponse response = mapToUserResponse(user, isVerified);
        log.debug("Returning current user: id={}, email={}, avatarUrl={}",
            response.getId(), response.getEmail(), response.getAvatarUrl());

        return response;
    }

    public User getCurrentUserEntity(HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        if (userId == null) {
            return null;
        }

        return userRepository.findById(userId).orElse(null);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        UUID userId = getUserIdFromRequest(request);
        String accessToken = getTokenFromCookie(request, AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME);

        if (userId != null && accessToken != null) {
            redisTokenService.deleteAllTokensForUser(userId, accessToken);
            log.info("Logged out user: { }", userId);
        }

        clearTokenCookies(response);
    }

    @Transactional
    public AuthResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = getTokenFromCookie(request, AuthTokenConstants.REFRESH_TOKEN_COOKIE_NAME);
        if (refreshToken == null) {
            throw new RuntimeException("Refresh token not found");
        }

        String oldAccessToken = getTokenFromCookie(request, AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME);

        UUID userId;
        try {
            userId = jwtService.extractUserIdFromRefreshToken(refreshToken);
            if (!jwtService.isRefreshTokenValid(refreshToken, userId)) {
                throw new RuntimeException("Invalid refresh token");
            }
        } catch (Exception e) {
            log.warn("Invalid refresh token format", e);
            throw new RuntimeException("Invalid refresh token");
        }

        if (!redisTokenService.isRefreshTokenValid(userId, refreshToken)) {
            throw new RuntimeException("Refresh token not found or expired");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getIsActive()) {
            throw new RuntimeException("User account is inactive");
        }

        Optional<UserLocalIdentity> localIdentityOpt = userLocalIdentityRepository.findByUserId(userId);
        Boolean isVerified = localIdentityOpt.map(UserLocalIdentity::getIsEmailVerified).orElse(false);

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        if (oldAccessToken != null && !oldAccessToken.isEmpty()) {
            try {
                redisTokenService.deleteAccessToken(oldAccessToken);
                log.debug("Revoked old access token during refresh for user: {}", user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to revoke old access token during refresh: {}", e.getMessage());
            }
        }

        redisTokenService.storeAccessToken(newAccessToken, user.getId());
        redisTokenService.rotateRefreshToken(user.getId(), newRefreshToken);

        setTokenCookies(response, newAccessToken, newRefreshToken);

        log.info("Refreshed tokens for user: {} (old token revoked: {})",
            user.getEmail(), oldAccessToken != null);

        return new AuthResponse(
            "Tokens refreshed successfully",
            mapToUserResponse(user, isVerified)
        );
    }

    public boolean isAuthenticated(HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        return userId != null;
    }

    /**
     * Verify user email with token
     */
    @Transactional
    public AuthResponse verifyEmail(String token) {
        log.info("Attempting to verify email with token");

        Optional<UserLocalIdentity> localIdentityOpt = userLocalIdentityRepository.findByEmailVerificationToken(token);
        if (localIdentityOpt.isEmpty()) {
            emailVerificationFailureCounter.increment();
            throw new RuntimeException("Invalid verification token");
        }

        UserLocalIdentity localIdentity = localIdentityOpt.get();

        if (!localIdentity.isEmailVerificationTokenValid()) {
            emailVerificationFailureCounter.increment();
            throw new RuntimeException("Verification token has expired");
        }

        if (localIdentity.getIsEmailVerified()) {
            emailVerificationFailureCounter.increment();
            throw new RuntimeException("Email is already verified");
        }

        localIdentity.setIsEmailVerified(true);
        localIdentity.setEmailVerificationToken(null);
        localIdentity.setEmailVerificationExpiresAt(null);
        localIdentity.setUpdatedAt(LocalDateTime.now());

        userLocalIdentityRepository.save(localIdentity);

        log.info("Successfully verified email for user: {}", localIdentity.getEmail());
        emailVerificationSuccessCounter.increment();

        return new AuthResponse(
            "Email verified successfully",
            mapToUserResponse(localIdentity.getUser(), true)
        );
    }

    /**
     * Request password reset
     */
    @Transactional
    public AuthResponse forgotPassword(ForgotPasswordRequest request) {
        log.info("Processing forgot password request for email: {}", request.getEmail());

        Optional<UserLocalIdentity> localIdentityOpt = userLocalIdentityRepository.findByEmail(request.getEmail());

        if (localIdentityOpt.isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", request.getEmail());
            passwordResetRequestCounter.increment();
            return new AuthResponse("If an account with this email exists, a password reset link has been sent.", null);
        }

        UserLocalIdentity localIdentity = localIdentityOpt.get();

        String resetToken = UUID.randomUUID().toString();
        LocalDateTime tokenExpiry = LocalDateTime.now().plusMinutes(passwordResetTokenExpiryMinutes);

        localIdentity.setPasswordResetToken(resetToken);
        localIdentity.setPasswordResetExpiresAt(tokenExpiry);
        localIdentity.setUpdatedAt(LocalDateTime.now());

        userLocalIdentityRepository.save(localIdentity);

        try {
            String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;
            boolean emailSent = emailService.sendPasswordResetEmail(
                request.getEmail(),
                passwordResetSubject,
                resetUrl
            );

            if (!emailSent) {
                log.warn("Failed to send password reset email to: {}", request.getEmail());
            }
        } catch (Exception e) {
            log.error("Error sending password reset email to: {}", request.getEmail(), e);
        }

        log.info("Password reset email sent to: {}", request.getEmail());
        passwordResetRequestCounter.increment();

        return new AuthResponse("If an account with this email exists, a password reset link has been sent.", null);
    }

    /**
     * Reset password with token
     */
    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        log.info("Attempting to reset password with token");

        Optional<UserLocalIdentity> localIdentityOpt =
            userLocalIdentityRepository.findByPasswordResetToken(request.getToken());
        if (localIdentityOpt.isEmpty()) {
            passwordResetFailureCounter.increment();
            throw new RuntimeException("Invalid reset token");
        }

        UserLocalIdentity localIdentity = localIdentityOpt.get();

        if (!localIdentity.isPasswordResetTokenValid()) {
            passwordResetFailureCounter.increment();
            throw new RuntimeException("Reset token has expired");
        }

        localIdentity.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        localIdentity.setPasswordResetToken(null);
        localIdentity.setPasswordResetExpiresAt(null);
        localIdentity.setLastPasswordChangeAt(LocalDateTime.now());
        localIdentity.setUpdatedAt(LocalDateTime.now());

        localIdentity.setFailedLoginAttempts(0);
        localIdentity.setLockedUntil(null);

        userLocalIdentityRepository.save(localIdentity);

        log.info("Successfully reset password for user: {}", localIdentity.getEmail());
        passwordResetSuccessCounter.increment();

        return new AuthResponse("Password reset successfully", null);
    }

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        String accessToken = getTokenFromCookie(request, AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME);
        if (accessToken == null) {
            return null;
        }

        if (!redisTokenService.isAccessTokenValid(accessToken)) {
            return null;
        }

        try {
            UUID userId = jwtService.extractUserIdFromAccessToken(accessToken);
            if (jwtService.isAccessTokenValid(accessToken, userId)) {
                return userId;
            }
        } catch (Exception e) {
            log.debug("Failed to extract user ID from access token", e);
        }

        return null;
    }

    private String getTokenFromCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
            .filter(cookie -> cookieName.equals(cookie.getName()))
            .findFirst()
            .map(Cookie::getValue)
            .orElse(null);
    }

    private void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        Cookie authCookie = new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, accessToken);
        authCookie.setHttpOnly(true);
        authCookie.setSecure(jwtCookieProperties.isSecure());
        authCookie.setPath("/");
        authCookie.setMaxAge(jwtCookieProperties.getAccessTokenExpiry());
        if (jwtCookieProperties.getDomain() != null && !jwtCookieProperties.getDomain().isEmpty()) {
            authCookie.setDomain(jwtCookieProperties.getDomain());
        }

        String secureFlag;
        if (jwtCookieProperties.isSecure()) {
            secureFlag = "Secure; ";
        } else {
            secureFlag = "";
        }
        response.setHeader("Set-Cookie", String.format(
            "%s=%s; Path=/; Max-Age=%d; HttpOnly; %sSameSite=%s",
            AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME,
            accessToken,
            jwtCookieProperties.getAccessTokenExpiry(),
            secureFlag,
            jwtCookieProperties.getSameSite()
        ));

        Cookie refreshCookie = new Cookie(AuthTokenConstants.REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(jwtCookieProperties.isSecure());
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(jwtCookieProperties.getRefreshTokenExpiry());

        if (jwtCookieProperties.getDomain() != null && !jwtCookieProperties.getDomain().isEmpty()) {
            refreshCookie.setDomain(jwtCookieProperties.getDomain());
        }

        response.addHeader("Set-Cookie", String.format(
            "%s=%s; Path=/; Max-Age=%d; HttpOnly; %sSameSite=%s",
            AuthTokenConstants.REFRESH_TOKEN_COOKIE_NAME,
            refreshToken,
            jwtCookieProperties.getRefreshTokenExpiry(),
            secureFlag,
            jwtCookieProperties.getSameSite()
        ));

        log.debug("Set HttpOnly cookies for authentication (secure: {}, sameSite: {})",
            jwtCookieProperties.isSecure(), jwtCookieProperties.getSameSite());
    }

    private void clearTokenCookies(HttpServletResponse response) {
        String secureFlag;
        if (jwtCookieProperties.isSecure()) {
            secureFlag = "Secure; ";
        } else {
            secureFlag = "";
        }
        response.setHeader("Set-Cookie", String.format(
            "%s=; Path=/; Max-Age=0; HttpOnly; %sSameSite=%s",
            AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME,
            secureFlag,
            jwtCookieProperties.getSameSite()
        ));

        response.addHeader("Set-Cookie", String.format(
            "%s=; Path=/; Max-Age=0; HttpOnly; %sSameSite=%s",
            AuthTokenConstants.REFRESH_TOKEN_COOKIE_NAME,
            secureFlag,
            jwtCookieProperties.getSameSite()
        ));

        log.debug("Cleared HttpOnly authentication cookies");
    }

    public String generateUniqueUsername(String baseUsername, String email) {
        String sanitizedBase = baseUsername;

        if (sanitizedBase == null || sanitizedBase.trim().isEmpty()) {
            if (email != null && email.contains("@")) {
                sanitizedBase = email.substring(0, email.indexOf("@"));
            } else {
                sanitizedBase = "user";
            }
        }

        sanitizedBase = sanitizedBase.replaceAll("[^a-zA-Z0-9_-]", "").toLowerCase();

        if (sanitizedBase.isEmpty()) {
            sanitizedBase = "user";
        }

        if (sanitizedBase.length() > 45) {
            sanitizedBase = sanitizedBase.substring(0, 45);
        }

        String username = sanitizedBase;
        int attempt = 0;

        while (userRepository.findByUsername(username).isPresent()) {
            attempt++;
            if (attempt == 1) {
                username = sanitizedBase + "_" + UUID.randomUUID().toString().substring(0, 4);
            } else {
                username = sanitizedBase + "_" + UUID.randomUUID().toString().substring(0, 8);
            }

            if (username.length() > 50) {
                username = username.substring(0, 50);
            }
        }

        return username;
    }

    private UserResponse mapToUserResponse(User user, Boolean isVerified) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getFirstname(),
            user.getLastname(),
            user.getIsActive(),
            user.getIsAdmin(),
            isVerified,
            user.getCreatedAt(),
            user.getLastLoginAt(),
            user.getAvatarUrl()
        );
    }
}