package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.LocalLoginRequest;
import area.server.AREA_Back.dto.RegisterRequest;
import area.server.AREA_Back.dto.TokenRefreshRequest;
import area.server.AREA_Back.dto.UserResponse;
import area.server.AREA_Back.service.Auth.AuthService;

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
            "testuser",
            "John",
            "Doe",
            true,
            false,
            true,
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
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", "testuser", "John", "Doe", null);
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
            RegisterRequest request = new RegisterRequest("existing@example.com", "password123", "testuser", "John", "Doe", null);
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
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", "testuser", "John", "Doe", null);
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
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", "testuser", "John", "Doe", null);
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
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", "testuser", "John", "Doe", null);
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
            RegisterRequest request = new RegisterRequest("existing@example.com", "password123", "testuser", "John", "Doe", null);
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

        @Test
        void registerShouldHandleEmptyEmail() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest("", "password123", "testuser", "John", "Doe", null);

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void registerShouldHandleShortPassword() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "123", "testuser", "John", "Doe", null);

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void registerShouldHandleInvalidEmailFormat() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest("invalid-email", "password123", "testuser", "John", "Doe", null);

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void registerShouldHandleEmptyFirstName() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", "testuser", "", "Doe", null);

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void registerShouldHandleEmptyLastName() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", "testuser", "John", "", null);

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void registerShouldHandleNullFirstName() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", "testuser", null, "Doe", null);

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void registerShouldHandleNullLastName() throws Exception {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", "testuser", "John", null, null);

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class LoginTests {

        @Test
        void loginShouldReturnOkWhenCredentialsValid() throws Exception {
            // Given
            LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "password123");
            when(authService.login(any(LocalLoginRequest.class), any(HttpServletResponse.class)))
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

            verify(authService).login(any(LocalLoginRequest.class), any(HttpServletResponse.class));
        }

        @Test
        void loginShouldReturnUnauthorizedWhenCredentialsInvalid() throws Exception {
            // Given
            LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "wrongpassword");
            when(authService.login(any(LocalLoginRequest.class), any(HttpServletResponse.class)))
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
            LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "password123");
            when(authService.login(any(LocalLoginRequest.class), any(HttpServletResponse.class)))
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
            LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "password123");
            when(authService.login(any(LocalLoginRequest.class), any(HttpServletResponse.class)))
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
            LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "password123");
            when(authService.login(any(LocalLoginRequest.class), any(HttpServletResponse.class)))
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
            LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "password123");
            when(authService.login(any(LocalLoginRequest.class), any(HttpServletResponse.class)))
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

        @Test
        void loginShouldHandleEmptyEmail() throws Exception {
            // Given
            LocalLoginRequest request = new LocalLoginRequest("", null, "password123");

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void loginShouldHandleEmptyPassword() throws Exception {
            // Given
            LocalLoginRequest request = new LocalLoginRequest("test@example.com", null, "");

            // When
            String requestJson = objectMapper.writeValueAsString(request);

            // Then
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());
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

        @Test
        void handleValidationExceptionShouldHandleEmptyFieldErrors() {
            // Given
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(List.of());

            MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
            when(exception.getBindingResult()).thenReturn(bindingResult);

            // When
            ResponseEntity<Map<String, Object>> response = authController.handleValidationException(exception);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Validation failed", response.getBody().get("error"));
        }
    }

    @Nested
    class VerifyEmailTests {

        @Test
        void verifyEmailShouldReturnOkWhenTokenValid() {
            // Given
            String token = "valid-token-123";
            when(authService.verifyEmail(token))
                .thenReturn(testAuthResponse);

            // When
            ResponseEntity<AuthResponse> response = authController.verifyEmail(token);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Success", response.getBody().getMessage());
            assertEquals("test@example.com", response.getBody().getUser().getEmail());
            verify(authService).verifyEmail(token);
        }

        @Test
        void verifyEmailShouldReturnBadRequestWhenTokenInvalid() {
            // Given
            String token = "invalid-token";
            when(authService.verifyEmail(token))
                .thenThrow(new RuntimeException("Invalid verification token"));

            // When
            ResponseEntity<AuthResponse> response = authController.verifyEmail(token);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Invalid verification token", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void verifyEmailShouldReturnBadRequestWhenTokenExpired() {
            // Given
            String token = "expired-token";
            when(authService.verifyEmail(token))
                .thenThrow(new RuntimeException("Verification token has expired"));

            // When
            ResponseEntity<AuthResponse> response = authController.verifyEmail(token);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Verification token has expired", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void verifyEmailShouldReturnInternalServerErrorWhenUnexpectedError() {
            // Given
            String token = "token-123";
            when(authService.verifyEmail(token))
                .thenAnswer(invocation -> {
                    throw new Exception("Database connection failed");
                });

            // When
            ResponseEntity<AuthResponse> response = authController.verifyEmail(token);

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Email verification failed", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }
    }

    @Nested
    class ForgotPasswordTests {

        @Test
        void forgotPasswordShouldReturnOkWhenEmailExists() {
            // Given
            area.server.AREA_Back.dto.ForgotPasswordRequest request = 
                new area.server.AREA_Back.dto.ForgotPasswordRequest("test@example.com");
            AuthResponse expectedResponse = new AuthResponse(
                "Password reset email sent", null);
            when(authService.forgotPassword(request))
                .thenReturn(expectedResponse);

            // When
            ResponseEntity<AuthResponse> response = authController.forgotPassword(request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Password reset email sent", response.getBody().getMessage());
            verify(authService).forgotPassword(request);
        }

        @Test
        void forgotPasswordShouldReturnOkEvenWhenEmailDoesNotExist() {
            // Given - For security, we return OK even if email doesn't exist
            area.server.AREA_Back.dto.ForgotPasswordRequest request = 
                new area.server.AREA_Back.dto.ForgotPasswordRequest("nonexistent@example.com");
            AuthResponse expectedResponse = new AuthResponse(
                "If an account with that email exists, a password reset link has been sent", null);
            when(authService.forgotPassword(request))
                .thenReturn(expectedResponse);

            // When
            ResponseEntity<AuthResponse> response = authController.forgotPassword(request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
        }

        @Test
        void forgotPasswordShouldReturnBadRequestWhenServiceThrowsException() {
            // Given
            area.server.AREA_Back.dto.ForgotPasswordRequest request = 
                new area.server.AREA_Back.dto.ForgotPasswordRequest("test@example.com");
            when(authService.forgotPassword(request))
                .thenThrow(new RuntimeException("Email service unavailable"));

            // When
            ResponseEntity<AuthResponse> response = authController.forgotPassword(request);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Email service unavailable", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void forgotPasswordShouldReturnInternalServerErrorWhenUnexpectedError() {
            // Given
            area.server.AREA_Back.dto.ForgotPasswordRequest request = 
                new area.server.AREA_Back.dto.ForgotPasswordRequest("test@example.com");
            when(authService.forgotPassword(request))
                .thenAnswer(invocation -> {
                    throw new Exception("Unexpected error");
                });

            // When
            ResponseEntity<AuthResponse> response = authController.forgotPassword(request);

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Failed to process password reset request", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }
    }

    @Nested
    class ResetPasswordTests {

        @Test
        void resetPasswordShouldReturnOkWhenTokenValidAndPasswordReset() {
            // Given
            area.server.AREA_Back.dto.ResetPasswordRequest request = 
                new area.server.AREA_Back.dto.ResetPasswordRequest("valid-token", "newPassword123");
            AuthResponse expectedResponse = new AuthResponse("Password reset successfully", testUserResponse);
            when(authService.resetPassword(request))
                .thenReturn(expectedResponse);

            // When
            ResponseEntity<AuthResponse> response = authController.resetPassword(request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Password reset successfully", response.getBody().getMessage());
            assertNotNull(response.getBody().getUser());
            verify(authService).resetPassword(request);
        }

        @Test
        void resetPasswordShouldReturnBadRequestWhenTokenInvalid() {
            // Given
            area.server.AREA_Back.dto.ResetPasswordRequest request = 
                new area.server.AREA_Back.dto.ResetPasswordRequest("invalid-token", "newPassword123");
            when(authService.resetPassword(request))
                .thenThrow(new RuntimeException("Invalid reset token"));

            // When
            ResponseEntity<AuthResponse> response = authController.resetPassword(request);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Invalid reset token", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void resetPasswordShouldReturnBadRequestWhenTokenExpired() {
            // Given
            area.server.AREA_Back.dto.ResetPasswordRequest request = 
                new area.server.AREA_Back.dto.ResetPasswordRequest("expired-token", "newPassword123");
            when(authService.resetPassword(request))
                .thenThrow(new RuntimeException("Reset token has expired"));

            // When
            ResponseEntity<AuthResponse> response = authController.resetPassword(request);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Reset token has expired", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void resetPasswordShouldReturnBadRequestWhenPasswordInvalid() {
            // Given
            area.server.AREA_Back.dto.ResetPasswordRequest request = 
                new area.server.AREA_Back.dto.ResetPasswordRequest("valid-token", "123");
            when(authService.resetPassword(request))
                .thenThrow(new RuntimeException("Password does not meet requirements"));

            // When
            ResponseEntity<AuthResponse> response = authController.resetPassword(request);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Password does not meet requirements", response.getBody().getMessage());
        }

        @Test
        void resetPasswordShouldReturnInternalServerErrorWhenUnexpectedError() {
            // Given
            area.server.AREA_Back.dto.ResetPasswordRequest request = 
                new area.server.AREA_Back.dto.ResetPasswordRequest("valid-token", "newPassword123");
            when(authService.resetPassword(request))
                .thenAnswer(invocation -> {
                    throw new Exception("Database error");
                });

            // When
            ResponseEntity<AuthResponse> response = authController.resetPassword(request);

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Password reset failed", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }
    }

    @Nested
    class AdditionalCoverageTests {

        @Test
        void registerShouldReturnInternalServerErrorOnNonRuntimeException() {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", "John", "Doe", null);
            // Use a checked exception wrapped to trigger the Exception catch block
            when(authService.register(any(RegisterRequest.class), any(HttpServletResponse.class)))
                .thenAnswer(invocation -> {
                    throw new Exception("Checked exception");
                });

            // When
            ResponseEntity<AuthResponse> response = authController.register(request, httpServletResponse);

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Registration failed", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void loginShouldReturnInternalServerErrorOnNonRuntimeException() {
            // Given
            LocalLoginRequest request = new LocalLoginRequest("test@example.com", "password123");
            // Use a checked exception wrapped to trigger the Exception catch block
            when(authService.login(any(LocalLoginRequest.class), any(HttpServletResponse.class)))
                .thenAnswer(invocation -> {
                    throw new Exception("Checked exception");
                });

            // When
            ResponseEntity<AuthResponse> response = authController.login(request, httpServletResponse);

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Login failed", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void getCurrentUserShouldReturnInternalServerErrorOnNonRuntimeException() {
            // Given
            when(authService.getCurrentUser(httpServletRequest))
                .thenAnswer(invocation -> {
                    throw new Exception("Checked exception");
                });

            // When
            ResponseEntity<?> response = authController.getCurrentUser(httpServletRequest);

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertInstanceOf(Map.class, response.getBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> errorResponse = (Map<String, Object>) response.getBody();
            assertEquals("Failed to get user information", errorResponse.get("error"));
        }

        @Test
        void refreshTokenShouldReturnInternalServerErrorOnNonRuntimeException() {
            // Given
            TokenRefreshRequest request = new TokenRefreshRequest();
            when(authService.refreshToken(httpServletRequest, httpServletResponse))
                .thenAnswer(invocation -> {
                    throw new Exception("Checked exception");
                });

            // When
            ResponseEntity<AuthResponse> response = authController.refreshToken(
                    request, httpServletRequest, httpServletResponse);

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Token refresh failed", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void verifyEmailShouldReturnInternalServerErrorOnNonRuntimeException() {
            // Given
            String token = "valid-token";
            when(authService.verifyEmail(token))
                .thenAnswer(invocation -> {
                    throw new Exception("Checked exception");
                });

            // When
            ResponseEntity<AuthResponse> response = authController.verifyEmail(token);

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Email verification failed", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void forgotPasswordShouldReturnInternalServerErrorOnNonRuntimeException() {
            // Given
            area.server.AREA_Back.dto.ForgotPasswordRequest request = 
                new area.server.AREA_Back.dto.ForgotPasswordRequest("test@example.com");
            when(authService.forgotPassword(request))
                .thenAnswer(invocation -> {
                    throw new Exception("Checked exception");
                });

            // When
            ResponseEntity<AuthResponse> response = authController.forgotPassword(request);

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Failed to process password reset request", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }

        @Test
        void resetPasswordShouldReturnInternalServerErrorOnNonRuntimeException() {
            // Given
            area.server.AREA_Back.dto.ResetPasswordRequest request = 
                new area.server.AREA_Back.dto.ResetPasswordRequest("valid-token", "newPassword123");
            when(authService.resetPassword(request))
                .thenAnswer(invocation -> {
                    throw new Exception("Checked exception");
                });

            // When
            ResponseEntity<AuthResponse> response = authController.resetPassword(request);

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Password reset failed", response.getBody().getMessage());
            assertNull(response.getBody().getUser());
        }
    }
}