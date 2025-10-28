package area.server.AREA_Back.controller;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.StoreTokenRequest;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.service.Auth.JwtService;
import area.server.AREA_Back.service.Auth.ServiceAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceTokenController Tests")
class ServiceTokenControllerTest {

    @Mock
    private ServiceAccountService serviceAccountService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private ServiceTokenController serviceTokenController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(serviceTokenController).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JSR310 module for LocalDateTime
        userId = UUID.randomUUID();
    }

    @Test
    void hasValidTokenReturnsTrue() throws Exception {
        String serviceName = "github";
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.hasValidToken(userId, serviceName)).thenReturn(true);

        mockMvc.perform(get("/api/service-tokens/" + serviceName + "/status")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasValidToken").value(true))
                .andExpect(jsonPath("$.serviceName").value(serviceName));

        verify(serviceAccountService).hasValidToken(userId, serviceName);
    }

    @Test
    void hasValidTokenReturnsFalse() throws Exception {
        String serviceName = "github";
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.hasValidToken(userId, serviceName)).thenReturn(false);

        mockMvc.perform(get("/api/service-tokens/" + serviceName + "/status")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasValidToken").value(false));
    }

    @Test
    void storeTokenSuccess() throws Exception {
        String serviceName = "github";
        StoreTokenRequest request = new StoreTokenRequest();
        request.setAccessToken("access123");
        request.setRefreshToken("refresh123");
        // Don't set expiresAt to avoid serialization issues
        
        Service service = new Service();
        service.setKey(serviceName);
        service.setName("GitHub");
        
        ServiceAccount account = new ServiceAccount();
        account.setId(UUID.randomUUID());
        account.setService(service); // Important: set the service
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.createOrUpdateServiceAccount(
                eq(userId), eq(serviceName), eq("access123"), eq("refresh123"), any(), any()))
                .thenReturn(account);

        mockMvc.perform(post("/api/service-tokens/" + serviceName)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk());

        verify(jwtService).extractUserIdFromAccessToken(anyString());
        verify(serviceAccountService).createOrUpdateServiceAccount(
                eq(userId), eq(serviceName), eq("access123"), eq("refresh123"), any(), any());
    }

    @Test
    void storeTokenUnauthorized() throws Exception {
        String serviceName = "github";
        StoreTokenRequest request = new StoreTokenRequest();
        request.setAccessToken("access123");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Invalid token"));

        mockMvc.perform(post("/api/service-tokens/" + serviceName)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hasValidTokenUnauthorized() throws Exception {
        String serviceName = "github";
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Invalid token"));

        mockMvc.perform(get("/api/service-tokens/" + serviceName + "/status")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTokenSuccess() throws Exception {
        String serviceName = "github";
        
        Service service = new Service();
        service.setKey(serviceName);
        service.setName("GitHub");
        
        ServiceAccount account = new ServiceAccount();
        account.setId(UUID.randomUUID());
        account.setService(service);
        account.setAccessTokenEnc("encrypted_access");
        account.setRefreshTokenEnc("encrypted_refresh");
        account.setExpiresAt(LocalDateTime.now().plusDays(1));
        
        Map<String, Object> scopes = new HashMap<>();
        scopes.put("repo", true);
        scopes.put("user", true);
        account.setScopes(scopes);
        
        account.setTokenVersion(1);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.getServiceAccount(userId, serviceName)).thenReturn(Optional.of(account));

        mockMvc.perform(get("/api/service-tokens/" + serviceName)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceKey").value(serviceName))
                .andExpect(jsonPath("$.hasAccessToken").value(true))
                .andExpect(jsonPath("$.hasRefreshToken").value(true))
                .andExpect(jsonPath("$.expired").value(false));

        verify(serviceAccountService).getServiceAccount(userId, serviceName);
    }

    @Test
    void getTokenNotFound() throws Exception {
        String serviceName = "github";
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.getServiceAccount(userId, serviceName)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/service-tokens/" + serviceName)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNotFound());

        verify(serviceAccountService).getServiceAccount(userId, serviceName);
    }

    @Test
    void getTokenUnauthorized() throws Exception {
        String serviceName = "github";
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Invalid token"));

        mockMvc.perform(get("/api/service-tokens/" + serviceName)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTokenWithExpiredToken() throws Exception {
        String serviceName = "github";
        
        Service service = new Service();
        service.setKey(serviceName);
        service.setName("GitHub");
        
        ServiceAccount account = new ServiceAccount();
        account.setId(UUID.randomUUID());
        account.setService(service);
        account.setAccessTokenEnc("encrypted_access");
        account.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.getServiceAccount(userId, serviceName)).thenReturn(Optional.of(account));

        mockMvc.perform(get("/api/service-tokens/" + serviceName)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(true));
    }

    @Test
    void getTokenWithNullExpiresAt() throws Exception {
        String serviceName = "github";
        
        Service service = new Service();
        service.setKey(serviceName);
        service.setName("GitHub");
        
        ServiceAccount account = new ServiceAccount();
        account.setId(UUID.randomUUID());
        account.setService(service);
        account.setAccessTokenEnc("encrypted_access");
        account.setExpiresAt(null); // No expiration
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.getServiceAccount(userId, serviceName)).thenReturn(Optional.of(account));

        mockMvc.perform(get("/api/service-tokens/" + serviceName)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(false));
    }

    @Test
    void deleteTokenSuccess() throws Exception {
        String serviceName = "github";
        
        Service service = new Service();
        service.setKey(serviceName);
        service.setName("GitHub");
        
        ServiceAccount account = new ServiceAccount();
        account.setId(UUID.randomUUID());
        account.setService(service);
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.getServiceAccount(userId, serviceName)).thenReturn(Optional.of(account));
        doNothing().when(serviceAccountService).revokeServiceAccount(userId, serviceName);

        mockMvc.perform(delete("/api/service-tokens/" + serviceName)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk());

        verify(serviceAccountService).getServiceAccount(userId, serviceName);
        verify(serviceAccountService).revokeServiceAccount(userId, serviceName);
    }

    @Test
    void deleteTokenNotFound() throws Exception {
        String serviceName = "github";
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.getServiceAccount(userId, serviceName)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/service-tokens/" + serviceName)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNotFound());

        verify(serviceAccountService).getServiceAccount(userId, serviceName);
        verify(serviceAccountService, never()).revokeServiceAccount(any(), any());
    }

    @Test
    void deleteTokenUnauthorized() throws Exception {
        String serviceName = "github";
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Invalid token"));

        mockMvc.perform(delete("/api/service-tokens/" + serviceName)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllTokensSuccess() throws Exception {
        Service githubService = new Service();
        githubService.setKey("github");
        githubService.setName("GitHub");
        
        Service googleService = new Service();
        googleService.setKey("google");
        googleService.setName("Google");
        
        ServiceAccount githubAccount = new ServiceAccount();
        githubAccount.setId(UUID.randomUUID());
        githubAccount.setService(githubService);
        githubAccount.setAccessTokenEnc("encrypted_github");
        githubAccount.setCreatedAt(LocalDateTime.now());
        githubAccount.setUpdatedAt(LocalDateTime.now());
        
        ServiceAccount googleAccount = new ServiceAccount();
        googleAccount.setId(UUID.randomUUID());
        googleAccount.setService(googleService);
        googleAccount.setAccessTokenEnc("encrypted_google");
        googleAccount.setRefreshTokenEnc("encrypted_refresh");
        googleAccount.setCreatedAt(LocalDateTime.now());
        googleAccount.setUpdatedAt(LocalDateTime.now());
        
        List<ServiceAccount> accounts = Arrays.asList(githubAccount, googleAccount);
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.getUserServiceAccounts(userId)).thenReturn(accounts);

        mockMvc.perform(get("/api/service-tokens")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].serviceKey").value("github"))
                .andExpect(jsonPath("$[1].serviceKey").value("google"));

        verify(serviceAccountService).getUserServiceAccounts(userId);
    }

    @Test
    void getAllTokensEmpty() throws Exception {
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.getUserServiceAccounts(userId)).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/api/service-tokens")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(serviceAccountService).getUserServiceAccounts(userId);
    }

    @Test
    void getAllTokensUnauthorized() throws Exception {
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Invalid token"));

        mockMvc.perform(get("/api/service-tokens")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void extractAccessTokenFromCookiesNoCookies() throws Exception {
        String serviceName = "github";
        
        // No cookies provided, should fail
        mockMvc.perform(get("/api/service-tokens/" + serviceName + "/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void extractAccessTokenFromCookiesWrongCookieName() throws Exception {
        String serviceName = "github";
        
        // Wrong cookie name
        mockMvc.perform(get("/api/service-tokens/" + serviceName + "/status")
                        .cookie(new Cookie("wrong_cookie_name", "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void convertToDtoWithAllFields() throws Exception {
        String serviceName = "github";
        
        Service service = new Service();
        service.setKey(serviceName);
        service.setName("GitHub");
        
        ServiceAccount account = new ServiceAccount();
        account.setId(UUID.randomUUID());
        account.setService(service);
        account.setRemoteAccountId("remote123");
        account.setAccessTokenEnc("encrypted_access");
        account.setRefreshTokenEnc("encrypted_refresh");
        account.setExpiresAt(LocalDateTime.now().plusDays(1));
        
        Map<String, Object> scopes = new HashMap<>();
        scopes.put("repo", true);
        scopes.put("user", true);
        scopes.put("admin", true);
        account.setScopes(scopes);
        
        account.setTokenVersion(5);
        account.setLastRefreshAt(LocalDateTime.now().minusHours(2));
        account.setCreatedAt(LocalDateTime.now().minusDays(10));
        account.setUpdatedAt(LocalDateTime.now().minusHours(1));
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.getServiceAccount(userId, serviceName)).thenReturn(Optional.of(account));

        mockMvc.perform(get("/api/service-tokens/" + serviceName)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceKey").value(serviceName))
                .andExpect(jsonPath("$.remoteAccountId").value("remote123"))
                .andExpect(jsonPath("$.hasAccessToken").value(true))
                .andExpect(jsonPath("$.hasRefreshToken").value(true))
                .andExpect(jsonPath("$.tokenVersion").value(5))
                .andExpect(jsonPath("$.scopes.length()").value(3))
                .andExpect(jsonPath("$.lastRefreshAt").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void convertToDtoWithMinimalFields() throws Exception {
        String serviceName = "github";
        
        Service service = new Service();
        service.setKey(serviceName);
        service.setName("GitHub");
        
        ServiceAccount account = new ServiceAccount();
        account.setId(UUID.randomUUID());
        account.setService(service);
        // No tokens, no expiration, no scopes
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(serviceAccountService.getServiceAccount(userId, serviceName)).thenReturn(Optional.of(account));

        mockMvc.perform(get("/api/service-tokens/" + serviceName)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAccessToken").value(false))
                .andExpect(jsonPath("$.hasRefreshToken").value(false))
                .andExpect(jsonPath("$.expired").value(false));
    }
}
