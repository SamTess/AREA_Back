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
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int ACCOUNT_LOCK_DURATION_MINUTES = 30;

    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletResponse response) {
        log.info("Attempting to register user with email: {}", request.getEmail());

        if (userLocalIdentityRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setIsActive(true);
        user.setIsAdmin(false);
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        log.debug("Created user with ID: {}", savedUser.getId());

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

        String accessToken = jwtService.generateAccessToken(savedUser.getId(), savedUser.getEmail());
        String refreshToken = jwtService.generateRefreshToken(savedUser.getId(), savedUser.getEmail());

        redisTokenService.storeAccessToken(accessToken, savedUser.getId());
        redisTokenService.storeRefreshToken(savedUser.getId(), refreshToken);

        setTokenCookies(response, accessToken, refreshToken);

        savedUser.setLastLoginAt(LocalDateTime.now());
        userRepository.save(savedUser);

        log.info("Successfully registered user: {}", savedUser.getEmail());

        return new AuthResponse(
            "User registered successfully",
            mapToUserResponse(savedUser),
            accessToken,
            refreshToken
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        log.info("Attempting to login user with email: {}", request.getEmail());

        Optional<UserLocalIdentity> localIdentityOpt = userLocalIdentityRepository.findByEmail(request.getEmail());
        if (localIdentityOpt.isEmpty()) {
            log.warn("Login attempt with non-existent email: {}", request.getEmail());
            throw new RuntimeException("Invalid credentials");
        }

        UserLocalIdentity localIdentity = localIdentityOpt.get();
        User user = localIdentity.getUser();

        if (localIdentity.isAccountLocked()) {
            log.warn("Login attempt on locked account: {}", request.getEmail());
            throw new RuntimeException("Account is temporarily locked due to failed login attempts");
        }

        if (!user.getIsActive()) {
            log.warn("Login attempt on inactive account: {}", request.getEmail());
            throw new RuntimeException("Account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), localIdentity.getPasswordHash())) {
            log.warn("Failed login attempt for email: {}", request.getEmail());

            userLocalIdentityRepository.incrementFailedLoginAttempts(request.getEmail());

            if (localIdentity.getFailedLoginAttempts() + 1 >= MAX_FAILED_ATTEMPTS) {
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(ACCOUNT_LOCK_DURATION_MINUTES);
                userLocalIdentityRepository.lockAccount(request.getEmail(), lockUntil);
                log.warn("Account locked due to failed attempts: {}", request.getEmail());
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

        log.info("Successfully logged in user: {}", user.getEmail());

        return new AuthResponse(
            "Login successful",
            mapToUserResponse(user),
            accessToken,
            refreshToken
        );
    }

    public UserResponse getCurrentUser(HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        if (userId == null) {
            throw new RuntimeException("User not authenticated");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        return mapToUserResponse(user);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        UUID userId = getUserIdFromRequest(request);
        String accessToken = getTokenFromCookie(request, ACCESS_TOKEN_COOKIE);

        if (userId != null && accessToken != null) {
            redisTokenService.deleteAllTokensForUser(userId, accessToken);
            log.info("Logged out user: {}", userId);
        }

        clearTokenCookies(response);
    }

    @Transactional
    public AuthResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = getTokenFromCookie(request, REFRESH_TOKEN_COOKIE);
        if (refreshToken == null) {
            throw new RuntimeException("Refresh token not found");
        }

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

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        redisTokenService.storeAccessToken(newAccessToken, user.getId());
        redisTokenService.rotateRefreshToken(user.getId(), newRefreshToken);

        setTokenCookies(response, newAccessToken, newRefreshToken);

        log.info("Refreshed tokens for user: {}", user.getEmail());

        return new AuthResponse(
            "Tokens refreshed successfully",
            mapToUserResponse(user),
            newAccessToken,
            newRefreshToken
        );
    }

    public boolean isAuthenticated(HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        return userId != null;
    }

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        String accessToken = getTokenFromCookie(request, ACCESS_TOKEN_COOKIE);
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
        Cookie accessCookie = new Cookie(ACCESS_TOKEN_COOKIE, accessToken);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(false);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(ACCESS_TOKEN_COOKIE_MAX_AGE);
        response.addCookie(accessCookie);

        Cookie refreshCookie = new Cookie(REFRESH_TOKEN_COOKIE, refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(REFRESH_TOKEN_COOKIE_MAX_AGE);
        response.addCookie(refreshCookie);
    }

    private void clearTokenCookies(HttpServletResponse response) {
        Cookie accessCookie = new Cookie(ACCESS_TOKEN_COOKIE, "");
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(false);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(0);
        response.addCookie(accessCookie);

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