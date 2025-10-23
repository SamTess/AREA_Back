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

import java.util.List;
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
}
