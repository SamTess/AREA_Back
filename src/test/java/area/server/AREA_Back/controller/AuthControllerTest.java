package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.LoginRequest;
import area.server.AREA_Back.dto.RegisterRequest;
import area.server.AREA_Back.dto.TokenRefreshRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private HttpServletResponse httpServletResponse;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID testUserId;
    private UserResponse testUserResponse;
    private AuthResponse testAuthResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        objectMapper = new ObjectMapper();

        testUserId = UUID.randomUUID();
        testUserResponse = new UserResponse(
            testUserId,
            "test@example.com",
            true,
            false,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            "https://example.com/avatar.jpg"
        );
        testAuthResponse = new AuthResponse("Success", testUserResponse);
    }

    @Nested
    class RegisterTests {

        @Test
        void registerShouldReturnCreatedWhenRegistrationSuccessful() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", null, null, null);
            when(authService.register(any(RegisterRequest.class), any(HttpServletResponse.class)))
                .thenReturn(testAuthResponse);

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("Success"))
                    .andExpect(jsonPath("$.user.email").value("test@example.com"));

            verify(authService).register(any(RegisterRequest.class), any(HttpServletResponse.class));
        }

        @Test
        void registerShouldReturnConflictWhenEmailAlreadyExists() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest("existing@example.com", "password123", null, null, null);
            when(authService.register(any(RegisterRequest.class), any(HttpServletResponse.class)))
                .thenThrow(new RuntimeException("Email already registered"));

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Email already registered"))
                    .andExpect(jsonPath("$.user").isEmpty());
        }

        @Test
        void registerShouldReturnInternalServerErrorWhenUnexpectedExceptionOccurs() {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", null, null, null);
            when(authService.register(any(RegisterRequest.class), any(HttpServletResponse.class)))
                .thenThrow(new RuntimeException("Database error"));

            // When
            ResponseEntity<AuthResponse> response = authController.register(request, httpServletResponse);

            // Then
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Database error", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void registerShouldReturnConflictWhenServiceThrowsRuntimeException() {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", null, null, null);
            when(authService.register(any(RegisterRequest.class), any(HttpServletResponse.class)))
                .thenThrow(new RuntimeException("Service error"));

            // When
            ResponseEntity<AuthResponse> response = authController.register(request, httpServletResponse);

            // Then
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Service error", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void registerUsingDirectControllerCallShouldReturnCreatedWhenSuccessful() {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", null, null, null);
            when(authService.register(any(RegisterRequest.class), any(HttpServletResponse.class)))
                .thenReturn(testAuthResponse);

            // When
            ResponseEntity<AuthResponse> response = authController.register(request, httpServletResponse);

            // Then
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Success", response.getBody().getMessage());
            assertEquals("test@example.com", response.getBody().getUser().getEmail());
            verify(authService).register(request, httpServletResponse);
        }

        @Test
        void registerUsingDirectControllerCallShouldReturnConflictWhenEmailExists() {
            // Given
            RegisterRequest request = new RegisterRequest("existing@example.com", "password123", null, null, null);
            when(authService.register(any(RegisterRequest.class), any(HttpServletResponse.class)))
                .thenThrow(new RuntimeException("Email already registered"));

            // When
            ResponseEntity<AuthResponse> response = authController.register(request, httpServletResponse);

            // Then
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Email already registered", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }
    }

    @Nested
    class LoginTests {

        @Test
        void loginShouldReturnOkWhenCredentialsValid() throws Exception {
            // Given
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            when(authService.login(any(LoginRequest.class), any(HttpServletResponse.class)))
                .thenReturn(testAuthResponse);

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Success"))
                    .andExpect(jsonPath("$.user.email").value("test@example.com"));

            verify(authService).login(any(LoginRequest.class), any(HttpServletResponse.class));
        }

        @Test
        void loginShouldReturnUnauthorizedWhenCredentialsInvalid() throws Exception {
            // Given
            LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");
            when(authService.login(any(LoginRequest.class), any(HttpServletResponse.class)))
                .thenThrow(new RuntimeException("Invalid credentials"));

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid credentials"))
                    .andExpect(jsonPath("$.user").isEmpty());
        }

        @Test
        void loginShouldReturnLockedWhenAccountLocked() throws Exception {
            // Given
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            when(authService.login(any(LoginRequest.class), any(HttpServletResponse.class)))
                .thenThrow(new RuntimeException("Account locked due to failed attempts"));

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.message").value("Account locked due to failed attempts"))
                    .andExpect(jsonPath("$.user").isEmpty());
        }

        @Test
        void loginUsingDirectControllerCallShouldReturnOkWhenSuccessful() {
            // Given
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            when(authService.login(any(LoginRequest.class), any(HttpServletResponse.class)))
                .thenReturn(testAuthResponse);

            // When
            ResponseEntity<AuthResponse> response = authController.login(request, httpServletResponse);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Success", response.getBody().getMessage());
            assertEquals("test@example.com", response.getBody().getUser().getEmail());
        }

        @Test
        void loginUsingDirectControllerCallShouldReturnLockedWhenAccountLocked() {
            // Given
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            when(authService.login(any(LoginRequest.class), any(HttpServletResponse.class)))
                .thenThrow(new RuntimeException("Account is locked"));

            // When
            ResponseEntity<AuthResponse> response = authController.login(request, httpServletResponse);

            // Then
            assertEquals(HttpStatus.LOCKED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Account is locked", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void loginShouldReturnInternalServerErrorWhenUnexpectedExceptionOccurs() throws Exception {
            // Given
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            when(authService.login(any(LoginRequest.class), any(HttpServletResponse.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Unexpected error"))
                    .andExpect(jsonPath("$.user").isEmpty());
        }
    }

    @Nested
    class GetCurrentUserTests {

        @Test
        void getCurrentUserShouldReturnUserWhenAuthenticated() {
            // Given
            when(authService.getCurrentUser(httpServletRequest))
                .thenReturn(testUserResponse);

            // When
            ResponseEntity<?> response = authController.getCurrentUser(httpServletRequest);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertInstanceOf(UserResponse.class, response.getBody());
            UserResponse userResponse = (UserResponse) response.getBody();
            assertEquals("test@example.com", userResponse.getEmail());
            verify(authService).getCurrentUser(httpServletRequest);
        }

        @Test
        void getCurrentUserShouldReturnUnauthorizedWhenNotAuthenticated() {
            // Given
            when(authService.getCurrentUser(httpServletRequest))
                .thenThrow(new RuntimeException("User not authenticated"));

            // When
            ResponseEntity<?> response = authController.getCurrentUser(httpServletRequest);

            // Then
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertInstanceOf(Map.class, response.getBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> errorResponse = (Map<String, Object>) response.getBody();
            assertEquals("User not authenticated", errorResponse.get("error"));
        }

        @Test
        void getCurrentUserShouldReturnInternalServerErrorWhenUnexpectedError() {
            // Given
            when(authService.getCurrentUser(httpServletRequest))
                .thenThrow(new RuntimeException("Database connection failed"));

            // When
            ResponseEntity<?> response = authController.getCurrentUser(httpServletRequest);

            // Then
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertInstanceOf(Map.class, response.getBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> errorResponse = (Map<String, Object>) response.getBody();
            assertEquals("Database connection failed", errorResponse.get("error"));
        }
    }

    @Nested
    class LogoutTests {

        @Test
        void logoutShouldReturnOkWhenSuccessful() {
            // Given
            doNothing().when(authService).logout(httpServletRequest, httpServletResponse);

            // When
            ResponseEntity<Map<String, String>> response = authController.logout(
                    httpServletRequest, httpServletResponse);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Logout successful", response.getBody().get("message"));
            verify(authService).logout(httpServletRequest, httpServletResponse);
        }

        @Test
        void logoutShouldReturnOkWhenExceptionOccurs() {
            // Given
            doThrow(new RuntimeException("Token cleanup failed"))
                .when(authService).logout(httpServletRequest, httpServletResponse);

            // When
            ResponseEntity<Map<String, String>> response = authController.logout(
                    httpServletRequest, httpServletResponse);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Logout completed", response.getBody().get("message"));
        }
    }

    @Nested
    class RefreshTokenTests {

        @Test
        void refreshTokenShouldReturnOkWhenTokenValid() {
            // Given
            TokenRefreshRequest request = new TokenRefreshRequest();
            when(authService.refreshToken(httpServletRequest, httpServletResponse))
                .thenReturn(testAuthResponse);

            // When
            ResponseEntity<AuthResponse> response = authController.refreshToken(
                    request, httpServletRequest, httpServletResponse);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Success", response.getBody().getMessage());
            assertEquals("test@example.com", response.getBody().getUser().getEmail());
            verify(authService).refreshToken(httpServletRequest, httpServletResponse);
        }

        @Test
        void refreshTokenShouldReturnUnauthorizedWhenTokenInvalid() {
            // Given
            TokenRefreshRequest request = new TokenRefreshRequest();
            when(authService.refreshToken(httpServletRequest, httpServletResponse))
                .thenThrow(new RuntimeException("Invalid refresh token"));

            // When
            ResponseEntity<AuthResponse> response = authController.refreshToken(
                    request, httpServletRequest, httpServletResponse);

            // Then
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Invalid refresh token", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void refreshTokenShouldReturnInternalServerErrorWhenUnexpectedError() {
            // Given
            TokenRefreshRequest request = new TokenRefreshRequest();
            when(authService.refreshToken(httpServletRequest, httpServletResponse))
                .thenThrow(new RuntimeException("Service unavailable"));

            // When
            ResponseEntity<AuthResponse> response = authController.refreshToken(
                    request, httpServletRequest, httpServletResponse);

            // Then
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Service unavailable", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void refreshTokenShouldWorkWhenRequestIsNull() {
            // Given
            when(authService.refreshToken(httpServletRequest, httpServletResponse))
                .thenReturn(testAuthResponse);

            // When
            ResponseEntity<AuthResponse> response = authController.refreshToken(
                    null, httpServletRequest, httpServletResponse);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Success", response.getBody().getMessage());
        }
    }

    @Nested
    class GetAuthStatusTests {

        @Test
        void getAuthStatusShouldReturnAuthenticatedWhenUserIsAuthenticated() {
            // Given
            when(authService.isAuthenticated(httpServletRequest)).thenReturn(true);

            // When
            ResponseEntity<Map<String, Object>> response = authController.getAuthStatus(httpServletRequest);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(true, response.getBody().get("authenticated"));
            assertNotNull(response.getBody().get("timestamp"));
            assertFalse(response.getBody().containsKey("error"));
            verify(authService).isAuthenticated(httpServletRequest);
        }

        @Test
        void getAuthStatusShouldReturnNotAuthenticatedWhenUserIsNotAuthenticated() {
            // Given
            when(authService.isAuthenticated(httpServletRequest)).thenReturn(false);

            // When
            ResponseEntity<Map<String, Object>> response = authController.getAuthStatus(httpServletRequest);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(false, response.getBody().get("authenticated"));
            assertNotNull(response.getBody().get("timestamp"));
            assertFalse(response.getBody().containsKey("error"));
        }

        @Test
        void getAuthStatusShouldReturnFalseWhenExceptionOccurs() {
            // Given
            when(authService.isAuthenticated(httpServletRequest))
                .thenThrow(new RuntimeException("Service error"));

            // When
            ResponseEntity<Map<String, Object>> response = authController.getAuthStatus(httpServletRequest);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(false, response.getBody().get("authenticated"));
            assertNotNull(response.getBody().get("timestamp"));
            assertEquals("Failed to check authentication status", response.getBody().get("error"));
        }
    }

    @Nested
    class ExceptionHandlerTests {

        @Test
        void handleRuntimeExceptionShouldReturnBadRequest() {
            // Given
            RuntimeException exception = new RuntimeException("Test runtime exception");

            // When
            ResponseEntity<Map<String, String>> response = authController.handleRuntimeException(exception);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Test runtime exception", response.getBody().get("error"));
        }

        @Test
        void handleValidationExceptionShouldReturnBadRequest() {
            // Given
            BindingResult bindingResult = mock(BindingResult.class);
            FieldError fieldError1 = new FieldError("registerRequest", "email", "Email is required");
            FieldError fieldError2 = new FieldError("registerRequest", "password", 
                    "Password must be at least 8 characters");
            when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

            MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
            when(exception.getBindingResult()).thenReturn(bindingResult);

            // When
            ResponseEntity<Map<String, Object>> response = authController.handleValidationException(exception);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Validation failed", response.getBody().get("error"));

            @SuppressWarnings("unchecked")
            Map<String, String> details = (Map<String, String>) response.getBody().get("details");
            assertNotNull(details);
            assertEquals("Email is required", details.get("email"));
            assertEquals("Password must be at least 8 characters", details.get("password"));
        }
    }
}