package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.LocalLoginRequest;
import area.server.AREA_Back.dto.RegisterRequest;
import area.server.AREA_Back.dto.TokenRefreshRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication management operations")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user", description = "Create a new user account with email and password")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        try {
            log.info("Registration request for email: {}", request.getEmail());
            AuthResponse authResponse = authService.register(request, response);
            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
        } catch (RuntimeException e) {
            log.error("Registration failed for email: {}", request.getEmail(), e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthResponse(e.getMessage(), null));
        } catch (Exception e) {
            log.error("Unexpected error during registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AuthResponse("Registration failed", null));
        }
    }

    @Operation(summary = "Login user", description = "Authenticate user with email and password")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "423", description = "Account locked due to failed attempts")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LocalLoginRequest request,
            HttpServletResponse response) {
        try {
            log.info("Login request for email: {}", request.getEmail());
            AuthResponse authResponse = authService.login(request, response);
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            log.warn("Login failed for email: {}", request.getEmail(), e);

            HttpStatus status = HttpStatus.UNAUTHORIZED;
            if (e.getMessage().contains("locked")) {
                status = HttpStatus.LOCKED;
            }

            return ResponseEntity.status(status)
                .body(new AuthResponse(e.getMessage(), null));
        } catch (Exception e) {
            log.error("Unexpected error during login", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AuthResponse("Login failed", null));
        }
    }

    @Operation(summary = "Get current user", description = "Get the currently authenticated user's information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User information retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        try {
            UserResponse user = authService.getCurrentUser(request);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            log.warn("Failed to get current user", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting current user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get user information"));
        }
    }

    @Operation(summary = "Logout user", description = "Logout the current user and clear authentication cookies")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logout successful"),
        @ApiResponse(responseCode = "200", description = "Logout completed (even if user wasn't authenticated)")
    })
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            authService.logout(request, response);
            log.info("User logged out successfully");
            return ResponseEntity.ok(Map.of("message", "Logout successful"));
        } catch (Exception e) {
            log.error("Error during logout", e);
            return ResponseEntity.ok(Map.of("message", "Logout completed"));
        }
    }

    @Operation(summary = "Refresh access token",
               description = "Generate new access and refresh tokens using the current refresh token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestBody(required = false) TokenRefreshRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        try {
            log.debug("Token refresh request");
            AuthResponse authResponse = authService.refreshToken(httpRequest, response);
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            log.warn("Token refresh failed", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthResponse(e.getMessage(), null));
        } catch (Exception e) {
            log.error("Unexpected error during token refresh", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new AuthResponse("Token refresh failed", null));
        }
    }

    @Operation(summary = "Check authentication status", description = "Check if the current user is authenticated")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Authentication status checked")
    })
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus(HttpServletRequest request) {
        try {
            boolean isAuthenticated = authService.isAuthenticated(request);
            return ResponseEntity.ok(Map.of(
                "authenticated", isAuthenticated,
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Error checking auth status", e);
            return ResponseEntity.ok(Map.of(
                "authenticated", false,
                "timestamp", System.currentTimeMillis(),
                "error", "Failed to check authentication status"
            ));
        }
    }

    /**
     * Global exception handler for authentication-related exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        log.error("Authentication runtime exception", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", e.getMessage()));
    }

    /**
     * Global exception handler for validation exceptions
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            org.springframework.web.bind.MethodArgumentNotValidException e) {
        log.warn("Validation error in auth request", e);

        Map<String, String> errors = new java.util.HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                "error", "Validation failed",
                "details", errors
            ));
    }
}