package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.OAuthProvider;
import area.server.AREA_Back.service.Auth.OAuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OAuthController
 * Tests OAuth provider listing, authorization, and token exchange endpoints
 */
@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

    @Mock
    private OAuthService oauthGoogleService;

    @Mock
    private HttpServletResponse httpServletResponse;

    private OAuthController oauthController;

    @BeforeEach
    void setUp() {
        // Setup mock OAuth service with lenient stubbing
        lenient().when(oauthGoogleService.getProviderKey()).thenReturn("google");
        lenient().when(oauthGoogleService.getProviderLabel()).thenReturn("Google");
        lenient().when(oauthGoogleService.getProviderLogoUrl()).thenReturn("/oauth-icons/google.svg");
        lenient().when(oauthGoogleService.getUserAuthUrl()).thenReturn("https://accounts.google.com/o/oauth2/v2/auth?client_id=test");
        lenient().when(oauthGoogleService.getClientId()).thenReturn("test-client-id");

        oauthController = new OAuthController(List.of(oauthGoogleService));
    }

    @Test
    void testGetProviders() {
        // When
        ResponseEntity<List<OAuthProvider>> response = oauthController.getProviders();

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        
        OAuthProvider provider = response.getBody().get(0);
        assertEquals("google", provider.getProviderKey());
        assertEquals("Google", provider.getProviderLabel());
        assertEquals("/oauth-icons/google.svg", provider.getProviderLogoUrl());
        assertEquals("https://accounts.google.com/o/oauth2/v2/auth?client_id=test", provider.getUserAuthUrl());
    }

    @Test
    void testGetProvidersWithMultipleProviders() {
        // Given
        OAuthService anotherService = mock(OAuthService.class);
        when(anotherService.getProviderKey()).thenReturn("github");
        when(anotherService.getProviderLabel()).thenReturn("GitHub");
        when(anotherService.getProviderLogoUrl()).thenReturn("/oauth-icons/github.svg");
        when(anotherService.getUserAuthUrl()).thenReturn("https://github.com/login/oauth/authorize");

        oauthController = new OAuthController(List.of(oauthGoogleService, anotherService));

        // When
        ResponseEntity<List<OAuthProvider>> response = oauthController.getProviders();

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void testGetProvidersWithNoProviders() {
        // Given
        oauthController = new OAuthController(List.of());

        // When
        ResponseEntity<List<OAuthProvider>> response = oauthController.getProviders();

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testAuthorizeWithValidProvider() {
        // When
        ResponseEntity<String> response = oauthController.authorize("google", httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        verify(httpServletResponse).setHeader("Location", "https://accounts.google.com/o/oauth2/v2/auth?client_id=test");
        verify(httpServletResponse).setStatus(HttpServletResponse.SC_FOUND);
    }

    @Test
    void testAuthorizeWithInvalidProvider() {
        // When
        ResponseEntity<String> response = oauthController.authorize("invalid-provider", httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("Provider not found"));
        verify(httpServletResponse, never()).setHeader(anyString(), anyString());
    }

    @Test
    void testAuthorizeCaseInsensitive() {
        // When
        ResponseEntity<String> response = oauthController.authorize("GOOGLE", httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
    }

    @Test
    void testAuthorizeWithException() {
        // Given
        when(oauthGoogleService.getUserAuthUrl()).thenThrow(new RuntimeException("Service error"));

        // When
        ResponseEntity<String> response = oauthController.authorize("google", httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().contains("Failed to redirect"));
    }

    @Test
    void testExchangeTokenWithValidProvider() {
        // Given
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("code", "valid-auth-code");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Authentication successful");

        when(oauthGoogleService.authenticate(any(OAuthLoginRequest.class), eq(httpServletResponse)))
            .thenReturn(authResponse);

        // When
        ResponseEntity<?> response = oauthController.exchangeToken("google", requestBody, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof AuthResponse);
        AuthResponse resultAuthResponse = (AuthResponse) response.getBody();
        assertNotNull(resultAuthResponse);
        assertNotNull(resultAuthResponse.getMessage());
        verify(oauthGoogleService).authenticate(any(OAuthLoginRequest.class), eq(httpServletResponse));
    }

    @Test
    void testExchangeTokenWithMissingCode() {
        // Given
        Map<String, String> requestBody = new HashMap<>();

        // When
        ResponseEntity<?> response = oauthController.exchangeToken("google", requestBody, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(oauthGoogleService, never()).authenticate(any(), any());
    }

    @Test
    void testExchangeTokenWithEmptyCode() {
        // Given
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("code", "");

        // When
        ResponseEntity<?> response = oauthController.exchangeToken("google", requestBody, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(oauthGoogleService, never()).authenticate(any(), any());
    }

    @Test
    void testExchangeTokenWithWhitespaceCode() {
        // Given
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("code", "   ");

        // When
        ResponseEntity<?> response = oauthController.exchangeToken("google", requestBody, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(oauthGoogleService, never()).authenticate(any(), any());
    }

    @Test
    void testExchangeTokenWithInvalidProvider() {
        // Given
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("code", "valid-code");

        // When
        ResponseEntity<?> response = oauthController.exchangeToken("invalid-provider", requestBody, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(oauthGoogleService, never()).authenticate(any(), any());
    }

    @Test
    void testExchangeTokenWithUnsupportedOperation() {
        // Given
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("code", "valid-code");

        when(oauthGoogleService.authenticate(any(OAuthLoginRequest.class), eq(httpServletResponse)))
            .thenThrow(new UnsupportedOperationException("Not implemented"));

        // When
        ResponseEntity<?> response = oauthController.exchangeToken("google", requestBody, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
    }

    @Test
    void testExchangeTokenWithException() {
        // Given
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("code", "valid-code");

        when(oauthGoogleService.authenticate(any(OAuthLoginRequest.class), eq(httpServletResponse)))
            .thenThrow(new RuntimeException("Authentication failed"));

        // When
        ResponseEntity<?> response = oauthController.exchangeToken("google", requestBody, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testExchangeTokenCaseInsensitiveProvider() {
        // Given
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("code", "valid-code");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Authentication successful");

        when(oauthGoogleService.authenticate(any(OAuthLoginRequest.class), eq(httpServletResponse)))
            .thenReturn(authResponse);

        // When
        ResponseEntity<?> response = oauthController.exchangeToken("GOOGLE", requestBody, httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testExchangeTokenNullRequestBody() {
        // When
        assertThrows(NullPointerException.class, () -> {
            oauthController.exchangeToken("google", null, httpServletResponse);
        });
    }

    @Test
    void testDiscordProviderInList() {
        OAuthService discordService = mock(OAuthService.class);
        when(discordService.getProviderKey()).thenReturn("discord");
        when(discordService.getProviderLabel()).thenReturn("Discord");
        when(discordService.getProviderLogoUrl())
            .thenReturn("https://img.icons8.com/color/48/discord-logo.png");
        when(discordService.getUserAuthUrl())
            .thenReturn("https://discord.com/api/oauth2/authorize?client_id=test");
        when(discordService.getClientId()).thenReturn("test-discord-client-id");

        oauthController = new OAuthController(List.of(oauthGoogleService, discordService));

        ResponseEntity<List<OAuthProvider>> response = oauthController.getProviders();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());

        OAuthProvider discordProvider = response.getBody().stream()
            .filter(p -> "discord".equals(p.getProviderKey()))
            .findFirst()
            .orElse(null);

        assertNotNull(discordProvider);
        assertEquals("Discord", discordProvider.getProviderLabel());
        assertTrue(discordProvider.getUserAuthUrl().contains("discord.com"));
    }

    @Test
    void testDiscordAuthorize() {
        OAuthService discordService = mock(OAuthService.class);
        when(discordService.getProviderKey()).thenReturn("discord");
        when(discordService.getUserAuthUrl())
            .thenReturn("https://discord.com/api/oauth2/authorize?client_id=test");

        oauthController = new OAuthController(List.of(discordService));

        ResponseEntity<String> response = oauthController.authorize("discord",
            httpServletResponse);

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        verify(httpServletResponse).setHeader("Location",
            "https://discord.com/api/oauth2/authorize?client_id=test");
    }

    @Test
    void testDiscordTokenExchange() {
        OAuthService discordService = mock(OAuthService.class);
        when(discordService.getProviderKey()).thenReturn("discord");

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("code", "discord-auth-code");

        AuthResponse authResponse = new AuthResponse();
        authResponse.setMessage("Discord authentication successful");

        when(discordService.authenticate(any(OAuthLoginRequest.class), eq(httpServletResponse)))
            .thenReturn(authResponse);

        oauthController = new OAuthController(List.of(discordService));

        ResponseEntity<?> response = oauthController.exchangeToken("discord",
            requestBody, httpServletResponse);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(discordService).authenticate(any(OAuthLoginRequest.class),
            eq(httpServletResponse));
    }
}
