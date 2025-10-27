package area.server.AREA_Back.controller;

import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.service.Auth.AuthService;
import area.server.AREA_Back.service.Auth.UserIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
@DisplayName("UserServiceConnectionController Tests")
class UserServiceConnectionControllerTest {

    @Mock
    private UserIdentityService userIdentityService;

    @Mock
    private AuthService authService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private UserServiceConnectionController controller;

    private MockMvc mockMvc;
    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        userId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("test@example.com");
    }

    @Test
    void getServiceConnectionStatusConnected() throws Exception {
        String serviceKey = "github";
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("github");
        oauth.setProviderUserId("123456");

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "github")).thenReturn(Optional.of(oauth));
        when(userIdentityService.canDisconnectService(userId, "github")).thenReturn(true);
        when(userIdentityService.getPrimaryOAuthProvider(userId)).thenReturn(Optional.of("github"));

        mockMvc.perform(get("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceKey").value(serviceKey))
                .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void getServiceConnectionStatusNotConnected() throws Exception {
        String serviceKey = "github";

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "github")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceKey").value(serviceKey))
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void getServiceConnectionStatusUnauthorized() throws Exception {
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(null);

        mockMvc.perform(get("/api/user/service-connection/github"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getConnectedServicesSuccess() throws Exception {
        UserOAuthIdentity oauth1 = new UserOAuthIdentity();
        oauth1.setProvider("github");
        UserOAuthIdentity oauth2 = new UserOAuthIdentity();
        oauth2.setProvider("google");

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getUserOAuthIdentities(userId)).thenReturn(List.of(oauth1, oauth2));
        when(userIdentityService.getPrimaryOAuthProvider(userId)).thenReturn(Optional.of("github"));
        when(userIdentityService.canDisconnectService(eq(userId), anyString())).thenReturn(true);

        mockMvc.perform(get("/api/user/connected-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getConnectedServicesUnauthorized() throws Exception {
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(null);

        mockMvc.perform(get("/api/user/connected-services"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disconnectServiceSuccess() throws Exception {
        String serviceKey = "github";
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("github");

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "github")).thenReturn(Optional.of(oauth));
        when(userIdentityService.canDisconnectService(userId, "github")).thenReturn(true);
        doNothing().when(userIdentityService).disconnectService(userId, "github");

        mockMvc.perform(delete("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(userIdentityService).disconnectService(userId, "github");
    }

    @Test
    void disconnectServiceForbiddenWhenPrimary() throws Exception {
        String serviceKey = "github";
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("github");

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "github")).thenReturn(Optional.of(oauth));
        when(userIdentityService.canDisconnectService(userId, "github")).thenReturn(false);

        mockMvc.perform(delete("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(userIdentityService, never()).disconnectService(any(), anyString());
    }

    @Test
    void disconnectServiceUnauthorized() throws Exception {
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(null);

        mockMvc.perform(delete("/api/user/service-connection/github"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disconnectServiceNotFound() throws Exception {
        String serviceKey = "github";

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "github")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Service connection not found"));

        verify(userIdentityService, never()).disconnectService(any(), anyString());
    }

    @Test
    void disconnectServiceThrowsIllegalStateException() throws Exception {
        String serviceKey = "github";
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("github");

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "github")).thenReturn(Optional.of(oauth));
        when(userIdentityService.canDisconnectService(userId, "github")).thenReturn(true);
        doThrow(new IllegalStateException("Cannot disconnect")).when(userIdentityService).disconnectService(userId, "github");

        mockMvc.perform(delete("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot disconnect"));
    }

    @Test
    void disconnectServiceThrowsGenericException() throws Exception {
        String serviceKey = "github";
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("github");

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "github")).thenReturn(Optional.of(oauth));
        when(userIdentityService.canDisconnectService(userId, "github")).thenReturn(true);
        doThrow(new RuntimeException("Database error")).when(userIdentityService).disconnectService(userId, "github");

        mockMvc.perform(delete("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to disconnect service"));
    }

    @Test
    void getServiceConnectionStatusWithGoogleService() throws Exception {
        String serviceKey = "google";
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("google");
        oauth.setProviderUserId("google123");

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "google")).thenReturn(Optional.of(oauth));
        when(userIdentityService.canDisconnectService(userId, "google")).thenReturn(true);
        when(userIdentityService.getPrimaryOAuthProvider(userId)).thenReturn(Optional.of("google"));

        mockMvc.perform(get("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceKey").value(serviceKey))
                .andExpect(jsonPath("$.serviceName").value("Google"))
                .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void getServiceConnectionStatusWithMicrosoftService() throws Exception {
        String serviceKey = "microsoft";
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("microsoft");
        oauth.setProviderUserId("ms123");

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "microsoft")).thenReturn(Optional.of(oauth));
        when(userIdentityService.canDisconnectService(userId, "microsoft")).thenReturn(true);
        when(userIdentityService.getPrimaryOAuthProvider(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceKey").value(serviceKey))
                .andExpect(jsonPath("$.serviceName").value("microsoft"))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.primaryAuth").value(false));
    }

    @Test
    void getServiceConnectionStatusWithUnknownService() throws Exception {
        String serviceKey = "unknown";

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceKey").value(serviceKey))
                .andExpect(jsonPath("$.serviceName").value("unknown"))
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void getServiceConnectionStatusWithOAuthEmail() throws Exception {
        String serviceKey = "github";
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("github");
        oauth.setProviderUserId("123456");
        Map<String, Object> tokenMeta = new HashMap<>();
        tokenMeta.put("email", "oauth@example.com");
        tokenMeta.put("name", "John Doe");
        oauth.setTokenMeta(tokenMeta);

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "github")).thenReturn(Optional.of(oauth));
        when(userIdentityService.canDisconnectService(userId, "github")).thenReturn(true);
        when(userIdentityService.getPrimaryOAuthProvider(userId)).thenReturn(Optional.of("github"));

        mockMvc.perform(get("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value("oauth@example.com"))
                .andExpect(jsonPath("$.userName").value("John Doe"));
    }

    @Test
    void getServiceConnectionStatusWithLoginInTokenMeta() throws Exception {
        String serviceKey = "github";
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("github");
        oauth.setProviderUserId("123456");
        Map<String, Object> tokenMeta = new HashMap<>();
        tokenMeta.put("login", "johndoe");
        oauth.setTokenMeta(tokenMeta);

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "github")).thenReturn(Optional.of(oauth));
        when(userIdentityService.canDisconnectService(userId, "github")).thenReturn(true);
        when(userIdentityService.getPrimaryOAuthProvider(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("johndoe"))
                .andExpect(jsonPath("$.userEmail").value("test@example.com"));
    }

    @Test
    void getServiceConnectionStatusWithNullTokenMeta() throws Exception {
        String serviceKey = "github";
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("github");
        oauth.setProviderUserId("123456");
        oauth.setTokenMeta(null);

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "github")).thenReturn(Optional.of(oauth));
        when(userIdentityService.canDisconnectService(userId, "github")).thenReturn(true);
        when(userIdentityService.getPrimaryOAuthProvider(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("test@example.com"))
                .andExpect(jsonPath("$.userEmail").value("test@example.com"));
    }

    @Test
    void getServiceConnectionStatusThrowsException() throws Exception {
        String serviceKey = "github";

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getOAuthIdentity(userId, "github")).thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/api/user/service-connection/" + serviceKey))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getConnectedServicesWithMultipleProvidersAndMetadata() throws Exception {
        UserOAuthIdentity oauth1 = new UserOAuthIdentity();
        oauth1.setProvider("github");
        oauth1.setProviderUserId("github123");
        Map<String, Object> tokenMeta1 = new HashMap<>();
        tokenMeta1.put("email", "github@example.com");
        tokenMeta1.put("name", "GitHub User");
        oauth1.setTokenMeta(tokenMeta1);

        UserOAuthIdentity oauth2 = new UserOAuthIdentity();
        oauth2.setProvider("google");
        oauth2.setProviderUserId("google456");
        Map<String, Object> tokenMeta2 = new HashMap<>();
        tokenMeta2.put("email", "google@example.com");
        tokenMeta2.put("login", "googleuser");
        oauth2.setTokenMeta(tokenMeta2);

        UserOAuthIdentity oauth3 = new UserOAuthIdentity();
        oauth3.setProvider("microsoft");
        oauth3.setProviderUserId("ms789");
        oauth3.setTokenMeta(null);

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getUserOAuthIdentities(userId)).thenReturn(List.of(oauth1, oauth2, oauth3));
        when(userIdentityService.getPrimaryOAuthProvider(userId)).thenReturn(Optional.of("github"));
        when(userIdentityService.canDisconnectService(userId, "github")).thenReturn(false);
        when(userIdentityService.canDisconnectService(userId, "google")).thenReturn(true);
        when(userIdentityService.canDisconnectService(userId, "microsoft")).thenReturn(true);

        mockMvc.perform(get("/api/user/connected-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void getConnectedServicesWithUnknownProvider() throws Exception {
        UserOAuthIdentity oauth = new UserOAuthIdentity();
        oauth.setProvider("unknown-provider");
        oauth.setProviderUserId("unknown123");

        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getUserOAuthIdentities(userId)).thenReturn(List.of(oauth));
        when(userIdentityService.getPrimaryOAuthProvider(userId)).thenReturn(Optional.empty());
        when(userIdentityService.canDisconnectService(eq(userId), anyString())).thenReturn(true);

        mockMvc.perform(get("/api/user/connected-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getConnectedServicesThrowsException() throws Exception {
        when(authService.getCurrentUserEntity(any(HttpServletRequest.class))).thenReturn(testUser);
        when(userIdentityService.getUserOAuthIdentities(userId)).thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/api/user/connected-services"))
                .andExpect(status().isInternalServerError());
    }
}
