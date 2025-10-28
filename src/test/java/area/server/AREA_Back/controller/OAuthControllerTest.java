package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.OAuthProvider;
import area.server.AREA_Back.service.Auth.OAuthService;
import area.server.AREA_Back.service.PKCEStore;
import jakarta.servlet.http.HttpServletRequest;
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
    private PKCEStore pkceStore;

    @Mock
    private HttpServletResponse httpServletResponse;

    @Mock
    private HttpServletRequest httpServletRequest;

    private OAuthController oauthController;

    @BeforeEach
    void setUp() {
        // Setup mock OAuth service with lenient stubbing
        lenient().when(oauthGoogleService.getProviderKey()).thenReturn("google");
        lenient().when(oauthGoogleService.getProviderLabel()).thenReturn("Google");
        lenient().when(oauthGoogleService.getProviderLogoUrl()).thenReturn("/oauth-icons/google.svg");
        lenient().when(oauthGoogleService.getUserAuthUrl()).thenReturn("https://accounts.google.com/o/oauth2/v2/auth?client_id=test");
        lenient().when(oauthGoogleService.getUserAuthUrl(anyString())).thenReturn("https://accounts.google.com/o/oauth2/v2/auth?client_id=test&state=test");
        lenient().when(oauthGoogleService.getClientId()).thenReturn("test-client-id");

        oauthController = new OAuthController(List.of(oauthGoogleService), pkceStore);
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
        lenient().when(anotherService.getProviderKey()).thenReturn("github");
        lenient().when(anotherService.getProviderLabel()).thenReturn("GitHub");
        lenient().when(anotherService.getProviderLogoUrl()).thenReturn("/oauth-icons/github.svg");
        lenient().when(anotherService.getUserAuthUrl()).thenReturn("https://github.com/login/oauth/authorize");
        lenient().when(anotherService.getUserAuthUrl(anyString())).thenReturn("https://github.com/login/oauth/authorize?state=test");

        oauthController = new OAuthController(List.of(oauthGoogleService, anotherService), pkceStore);

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
        oauthController = new OAuthController(List.of(), pkceStore);

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
        ResponseEntity<String> response = oauthController.authorize("google", "web", null, "S256", httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        verify(httpServletResponse).setHeader(eq("Location"), anyString());
        verify(httpServletResponse).setStatus(HttpServletResponse.SC_FOUND);
    }

    @Test
    void testAuthorizeWithInvalidProvider() {
        // When
        ResponseEntity<String> response = oauthController.authorize("invalid-provider", "web", null, "S256", httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("Provider not found"));
        verify(httpServletResponse, never()).setHeader(anyString(), anyString());
    }

    @Test
    void testAuthorizeCaseInsensitive() {
        // When
        ResponseEntity<String> response = oauthController.authorize("GOOGLE", "web", null, "S256", httpServletResponse);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
    }

    @Test
    void testAuthorizeWithException() {
        // Given
        when(oauthGoogleService.getUserAuthUrl(anyString())).thenThrow(new RuntimeException("Service error"));

        // When
        ResponseEntity<String> response = oauthController.authorize("google", "web", null, "S256", httpServletResponse);

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
        ResponseEntity<AuthResponse> response = oauthController.exchangeToken("google", requestBody, httpServletResponse, httpServletRequest);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getMessage());
        verify(oauthGoogleService).authenticate(any(OAuthLoginRequest.class), eq(httpServletResponse));
    }

    @Test
    void testExchangeTokenWithMissingCode() {
        // Given
        Map<String, String> requestBody = new HashMap<>();

        // When
        ResponseEntity<AuthResponse> response = oauthController.exchangeToken("google", requestBody, httpServletResponse, httpServletRequest);

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
        ResponseEntity<AuthResponse> response = oauthController.exchangeToken("google", requestBody, httpServletResponse, httpServletRequest);

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
        ResponseEntity<AuthResponse> response = oauthController.exchangeToken("google", requestBody, httpServletResponse, httpServletRequest);

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
        ResponseEntity<AuthResponse> response = oauthController.exchangeToken("invalid-provider", requestBody, httpServletResponse, httpServletRequest);

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
        ResponseEntity<AuthResponse> response = oauthController.exchangeToken("google", requestBody, httpServletResponse, httpServletRequest);

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
        ResponseEntity<AuthResponse> response = oauthController.exchangeToken("google", requestBody, httpServletResponse, httpServletRequest);

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
        ResponseEntity<AuthResponse> response = oauthController.exchangeToken("GOOGLE", requestBody, httpServletResponse, httpServletRequest);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testExchangeTokenNullRequestBody() {
        // When
        assertThrows(NullPointerException.class, () -> {
            oauthController.exchangeToken("google", null, httpServletResponse, httpServletRequest);
        });
    }

    @Test
    void testDiscordProviderInList() {
        OAuthService discordService = mock(OAuthService.class);
        lenient().when(discordService.getProviderKey()).thenReturn("discord");
        lenient().when(discordService.getProviderLabel()).thenReturn("Discord");
        lenient().when(discordService.getProviderLogoUrl())
            .thenReturn("https://img.icons8.com/color/48/discord-logo.png");
        lenient().when(discordService.getUserAuthUrl())
            .thenReturn("https://discord.com/api/oauth2/authorize?client_id=test");
        lenient().when(discordService.getUserAuthUrl(anyString()))
            .thenReturn("https://discord.com/api/oauth2/authorize?client_id=test&state=test");
        lenient().when(discordService.getClientId()).thenReturn("test-discord-client-id");

        oauthController = new OAuthController(List.of(oauthGoogleService, discordService), pkceStore);

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
        lenient().when(discordService.getProviderKey()).thenReturn("discord");
        lenient().when(discordService.getUserAuthUrl())
            .thenReturn("https://discord.com/api/oauth2/authorize?client_id=test");
        lenient().when(discordService.getUserAuthUrl(anyString()))
            .thenReturn("https://discord.com/api/oauth2/authorize?client_id=test&state=test");

        oauthController = new OAuthController(List.of(discordService), pkceStore);

        ResponseEntity<String> response = oauthController.authorize("discord",
            "web", null, "S256", httpServletResponse);

        assertNotNull(response);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        verify(httpServletResponse).setHeader(eq("Location"), anyString());
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

        oauthController = new OAuthController(List.of(discordService), pkceStore);

        ResponseEntity<AuthResponse> response = oauthController.exchangeToken("discord",
            requestBody, httpServletResponse, httpServletRequest);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(discordService).authenticate(any(OAuthLoginRequest.class),
            eq(httpServletResponse));
    }
}
