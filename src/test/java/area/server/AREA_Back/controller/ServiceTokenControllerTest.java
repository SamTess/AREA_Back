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
}
