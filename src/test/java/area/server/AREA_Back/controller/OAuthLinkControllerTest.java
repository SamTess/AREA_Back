package area.server.AREA_Back.controller;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.service.Auth.AuthService;
import area.server.AREA_Back.service.Auth.OAuthDiscordService;
import area.server.AREA_Back.service.Auth.OAuthGithubService;
import area.server.AREA_Back.service.Auth.OAuthGoogleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthLinkController Tests")
class OAuthLinkControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private OAuthDiscordService oauthDiscordService;

    @Mock
    private OAuthGithubService oauthGithubService;

    @Mock
    private OAuthGoogleService oauthGoogleService;

    @InjectMocks
    private OAuthLinkController oauthLinkController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private User testUser;
    private UserOAuthIdentity testIdentity;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(oauthLinkController).build();
        objectMapper = new ObjectMapper();
        
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setAvatarUrl("https://example.com/avatar.jpg");

        testIdentity = new UserOAuthIdentity();
        testIdentity.setId(UUID.randomUUID());
        testIdentity.setProviderUserId("discord-12345");
        testIdentity.setProvider("discord");
    }

    @Test
    @DisplayName("Should link Discord account successfully")
    void testLinkDiscordAccountSuccess() throws Exception {
        String authCode = "test-auth-code-discord";
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(oauthDiscordService.linkToExistingUser(eq(testUser), eq(authCode))).thenReturn(testIdentity);

        mockMvc.perform(post("/api/oauth-link/discord/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", authCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceKey").value("discord"))
                .andExpect(jsonPath("$.serviceName").value("Discord"))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.connectionType").value("OAUTH"))
                .andExpect(jsonPath("$.providerUserId").value("discord-12345"));

        verify(authService, times(1)).getCurrentUserEntity(any(HttpServletRequest.class));
        verify(oauthDiscordService, times(1)).linkToExistingUser(eq(testUser), eq(authCode));
    }

    @Test
    @DisplayName("Should link GitHub account successfully")
    void testLinkGithubAccountSuccess() throws Exception {
        String authCode = "test-auth-code-github";
        testIdentity.setProvider("github");
        testIdentity.setProviderUserId("github-67890");

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(oauthGithubService.linkToExistingUser(eq(testUser), eq(authCode))).thenReturn(testIdentity);

        mockMvc.perform(post("/api/oauth-link/github/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", authCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceKey").value("github"))
                .andExpect(jsonPath("$.serviceName").value("GitHub"))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.connectionType").value("OAUTH"))
                .andExpect(jsonPath("$.providerUserId").value("github-67890"));

        verify(authService, times(1)).getCurrentUserEntity(any(HttpServletRequest.class));
        verify(oauthGithubService, times(1)).linkToExistingUser(eq(testUser), eq(authCode));
    }

    @Test
    @DisplayName("Should link Google account successfully")
    void testLinkGoogleAccountSuccess() throws Exception {
        String authCode = "test-auth-code-google";
        testIdentity.setProvider("google");
        testIdentity.setProviderUserId("google-11111");

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(oauthGoogleService.linkToExistingUser(eq(testUser), eq(authCode))).thenReturn(testIdentity);

        mockMvc.perform(post("/api/oauth-link/google/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", authCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceKey").value("google"))
                .andExpect(jsonPath("$.serviceName").value("Google"))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.connectionType").value("OAUTH"))
                .andExpect(jsonPath("$.providerUserId").value("google-11111"));

        verify(authService, times(1)).getCurrentUserEntity(any(HttpServletRequest.class));
        verify(oauthGoogleService, times(1)).linkToExistingUser(eq(testUser), eq(authCode));
    }

    @Test
    @DisplayName("Should return 401 when user is not authenticated")
    void testLinkAccountUnauthorized() throws Exception {
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(null);

        mockMvc.perform(post("/api/oauth-link/discord/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "test-code"))))
                .andExpect(status().isUnauthorized());

        verify(authService, times(1)).getCurrentUserEntity(any(HttpServletRequest.class));
        verifyNoInteractions(oauthDiscordService);
    }

    @Test
    @DisplayName("Should return 400 when authorization code is missing")
    void testLinkAccountMissingCode() throws Exception {
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);

        mockMvc.perform(post("/api/oauth-link/discord/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());

        verify(authService, times(1)).getCurrentUserEntity(any(HttpServletRequest.class));
        verifyNoInteractions(oauthDiscordService);
    }

    @Test
    @DisplayName("Should return 400 when authorization code is empty")
    void testLinkAccountEmptyCode() throws Exception {
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);

        mockMvc.perform(post("/api/oauth-link/discord/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", ""))))
                .andExpect(status().isBadRequest());

        verify(authService, times(1)).getCurrentUserEntity(any(HttpServletRequest.class));
        verifyNoInteractions(oauthDiscordService);
    }

    @Test
    @DisplayName("Should return 404 for unsupported provider")
    void testLinkAccountUnsupportedProvider() throws Exception {
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);

        mockMvc.perform(post("/api/oauth-link/unsupported/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "test-code"))))
                .andExpect(status().isNotFound());

        verify(authService, times(1)).getCurrentUserEntity(any(HttpServletRequest.class));
    }

    @Test
    @DisplayName("Should return 409 when account is already linked")
    void testLinkAccountAlreadyLinked() throws Exception {
        String authCode = "test-auth-code";
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(oauthDiscordService.linkToExistingUser(eq(testUser), eq(authCode)))
                .thenThrow(new RuntimeException("This Discord account is already linked to another user"));

        mockMvc.perform(post("/api/oauth-link/discord/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", authCode))))
                .andExpect(status().isConflict());

        verify(authService, times(1)).getCurrentUserEntity(any(HttpServletRequest.class));
        verify(oauthDiscordService, times(1)).linkToExistingUser(eq(testUser), eq(authCode));
    }

    @Test
    @DisplayName("Should return 400 when email is required but missing")
    void testLinkAccountEmailRequired() throws Exception {
        String authCode = "test-auth-code";
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(oauthGithubService.linkToExistingUser(eq(testUser), eq(authCode)))
                .thenThrow(new RuntimeException("User email is required but not provided"));

        mockMvc.perform(post("/api/oauth-link/github/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", authCode))))
                .andExpect(status().isBadRequest());

        verify(authService, times(1)).getCurrentUserEntity(any(HttpServletRequest.class));
        verify(oauthGithubService, times(1)).linkToExistingUser(eq(testUser), eq(authCode));
    }

    @Test
    @DisplayName("Should return 500 on unexpected runtime exception")
    void testLinkAccountRuntimeException() throws Exception {
        String authCode = "test-auth-code";
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(oauthDiscordService.linkToExistingUser(eq(testUser), eq(authCode)))
                .thenThrow(new RuntimeException("Unexpected error occurred"));

        mockMvc.perform(post("/api/oauth-link/discord/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", authCode))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should return 500 on unexpected checked exception")
    void testLinkAccountCheckedException() throws Exception {
        String authCode = "test-auth-code";
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(oauthGoogleService.linkToExistingUser(eq(testUser), eq(authCode)))
                .thenThrow(new IllegalStateException("Service unavailable"));

        mockMvc.perform(post("/api/oauth-link/google/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", authCode))))
                .andExpect(status().isInternalServerError());
    }
}
