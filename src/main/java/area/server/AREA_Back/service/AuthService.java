package area.server.AREA_Back.service;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.LoginRequest;
import area.server.AREA_Back.dto.RegisterRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserLocalIdentity;
import area.server.AREA_Back.repository.UserLocalIdentityRepository;
import area.server.AREA_Back.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final int ACCESS_TOKEN_COOKIE_MAX_AGE = 15 * 60;
    private static final int REFRESH_TOKEN_COOKIE_MAX_AGE = 7 * 24 * 60 * 60;

    /**
     * Register a new user
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletResponse response) {
        log.info("Attempting to register user with email: {}", request.getEmail());

        // Check if email already exists
        if (userLocalIdentityRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Create user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setIsActive(true);
        user.setIsAdmin(false);
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        log.debug("Created user with ID: {}", savedUser.getId());

        // Create local identity
        UserLocalIdentity localIdentity = new UserLocalIdentity();
        localIdentity.setUser(savedUser);
        localIdentity.setEmail(request.getEmail());
        localIdentity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        localIdentity.setIsEmailVerified(false); // TODO: Implement email verification
        localIdentity.setFailedLoginAttempts(0);
        localIdentity.setCreatedAt(LocalDateTime.now());
        localIdentity.setUpdatedAt(LocalDateTime.now());

        userLocalIdentityRepository.save(localIdentity);
        log.debug("Created local identity for user: {}", savedUser.getId());

        // Generate tokens and set cookies
        String accessToken = jwtService.generateAccessToken(savedUser.getId(), savedUser.getEmail());
        String refreshToken = jwtService.generateRefreshToken(savedUser.getId(), savedUser.getEmail());

        // Store tokens in Redis
        redisTokenService.storeAccessToken(accessToken, savedUser.getId());
        redisTokenService.storeRefreshToken(savedUser.getId(), refreshToken);

        // Set httpOnly cookies
        setTokenCookies(response, accessToken, refreshToken);

        // Update last login
        savedUser.setLastLoginAt(LocalDateTime.now());
        userRepository.save(savedUser);

        log.info("Successfully registered user: {}", savedUser.getEmail());

        return new AuthResponse(
            "User registered successfully",
            mapToUserResponse(savedUser)
        );
    }

    /**
     * Login user with email and password
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        log.info("Attempting to login user with email: {}", request.getEmail());

        // Find user by email
        Optional<UserLocalIdentity> localIdentityOpt = userLocalIdentityRepository.findByEmail(request.getEmail());
        if (localIdentityOpt.isEmpty()) {
            log.warn("Login attempt with non-existent email: {}", request.getEmail());
            throw new RuntimeException("Invalid credentials");
        }

        UserLocalIdentity localIdentity = localIdentityOpt.get();
        User user = localIdentity.getUser();

        // Check if account is locked
        if (localIdentity.isAccountLocked()) {
            log.warn("Login attempt on locked account: {}", request.getEmail());
            throw new RuntimeException("Account is temporarily locked due to failed login attempts");
        }

        // Check if user is active
        if (!user.getIsActive()) {
            log.warn("Login attempt on inactive account: {}", request.getEmail());
            throw new RuntimeException("Account is inactive");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), localIdentity.getPasswordHash())) {
            log.warn("Failed login attempt for email: {}", request.getEmail());

            // Increment failed attempts
            userLocalIdentityRepository.incrementFailedLoginAttempts(request.getEmail());

            // Lock account if too many failures (5 attempts)
            if (localIdentity.getFailedLoginAttempts() + 1 >= 5) {
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(30); // 30 minutes lock
                userLocalIdentityRepository.lockAccount(request.getEmail(), lockUntil);
                log.warn("Account locked due to failed attempts: {}", request.getEmail());
            }

            throw new RuntimeException("Invalid credentials");
        }

        // Reset failed login attempts on successful login
        userLocalIdentityRepository.resetFailedLoginAttempts(request.getEmail());

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        // Store tokens in Redis
        redisTokenService.storeAccessToken(accessToken, user.getId());
        redisTokenService.storeRefreshToken(user.getId(), refreshToken);

        // Set httpOnly cookies
        setTokenCookies(response, accessToken, refreshToken);

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Successfully logged in user: {}", user.getEmail());

        return new AuthResponse(
            "Login successful",
            mapToUserResponse(user)
        );
    }

    /**
     * Get current authenticated user
     */
    public UserResponse getCurrentUser(HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        if (userId == null) {
            throw new RuntimeException("User not authenticated");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        return mapToUserResponse(user);
    }

    /**
     * Logout user - clear cookies and delete tokens from Redis
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        UUID userId = getUserIdFromRequest(request);
        String accessToken = getTokenFromCookie(request, ACCESS_TOKEN_COOKIE);

        if (userId != null && accessToken != null) {
            // Delete tokens from Redis
            redisTokenService.deleteAllTokensForUser(userId, accessToken);
            log.info("Logged out user: {}", userId);
        }

        // Clear cookies
        clearTokenCookies(response);
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    public AuthResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = getTokenFromCookie(request, REFRESH_TOKEN_COOKIE);
        if (refreshToken == null) {
            throw new RuntimeException("Refresh token not found");
        }

        // Validate refresh token with JWT service
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

        // Validate refresh token in Redis
        if (!redisTokenService.isRefreshTokenValid(userId, refreshToken)) {
            throw new RuntimeException("Refresh token not found or expired");
        }

        // Get user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getIsActive()) {
            throw new RuntimeException("User account is inactive");
        }

        // Generate new tokens
        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        // Store new tokens in Redis and remove old ones
        redisTokenService.storeAccessToken(newAccessToken, user.getId());
        redisTokenService.rotateRefreshToken(user.getId(), newRefreshToken);

        // Set new cookies
        setTokenCookies(response, newAccessToken, newRefreshToken);

        log.info("Refreshed tokens for user: {}", user.getEmail());

        return new AuthResponse(
            "Tokens refreshed successfully",
            mapToUserResponse(user)
        );
    }

    /**
     * Validate if user is authenticated based on request cookies
     */
    public boolean isAuthenticated(HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        return userId != null;
    }

    // Private helper methods

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        String accessToken = getTokenFromCookie(request, ACCESS_TOKEN_COOKIE);
        if (accessToken == null) {
            return null;
        }

        // Validate token in Redis first
        if (!redisTokenService.isAccessTokenValid(accessToken)) {
            return null;
        }

        // Then validate JWT structure and extract user ID
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
        // Access token cookie
        Cookie accessCookie = new Cookie(ACCESS_TOKEN_COOKIE, accessToken);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(false); // Set to true in production with HTTPS
        accessCookie.setPath("/");
        accessCookie.setMaxAge(ACCESS_TOKEN_COOKIE_MAX_AGE);
        response.addCookie(accessCookie);

        // Refresh token cookie
        Cookie refreshCookie = new Cookie(REFRESH_TOKEN_COOKIE, refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false); // Set to true in production with HTTPS
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(REFRESH_TOKEN_COOKIE_MAX_AGE);
        response.addCookie(refreshCookie);
    }

    private void clearTokenCookies(HttpServletResponse response) {
        // Clear access token cookie
        Cookie accessCookie = new Cookie(ACCESS_TOKEN_COOKIE, "");
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(false);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(0);
        response.addCookie(accessCookie);

        // Clear refresh token cookie
        Cookie refreshCookie = new Cookie(REFRESH_TOKEN_COOKIE, "");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(0);
        response.addCookie(refreshCookie);
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getIsActive(),
            user.getIsAdmin(),
            user.getCreatedAt(),
            user.getConfirmedAt(),
            user.getLastLoginAt(),
            user.getAvatarUrl()
        );
    }
}