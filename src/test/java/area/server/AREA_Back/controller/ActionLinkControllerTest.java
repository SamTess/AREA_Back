package area.server.AREA_Back.controller;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.ActionLinkResponse;
import area.server.AREA_Back.dto.BatchCreateActionLinksRequest;
import area.server.AREA_Back.dto.CreateActionLinkRequest;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.service.Area.ActionLinkService;
import area.server.AREA_Back.service.Auth.JwtService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActionLinkController Tests")
class ActionLinkControllerTest {

    @Mock
    private ActionLinkService actionLinkService;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private ActionLinkController actionLinkController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    private UUID areaId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(actionLinkController).build();
        areaId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void createActionLinkForbiddenWhenUserNotOwner() throws Exception {
        CreateActionLinkRequest req = new CreateActionLinkRequest();
        req.setSourceActionInstanceId(UUID.randomUUID());
        req.setTargetActionInstanceId(UUID.randomUUID());

        Area area = new Area();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/" + areaId + "/links")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());

        verify(areaRepository, times(1)).findById(areaId);
    }

    @Test
    void getActionLinksReturnsListWhenAuthorized() throws Exception {
        Area area = new Area();
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

    ActionLinkResponse resp = new ActionLinkResponse();
    // keep default fields; tests will assert array content exists

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionLinkService.getActionLinksByArea(areaId)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/areas/" + areaId + "/links")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());

        verify(actionLinkService, times(1)).getActionLinksByArea(areaId);
    }

    @Test
    void deleteActionLinkNoContentWhenAuthorized() throws Exception {
        Area area = new Area();
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        UUID src = UUID.randomUUID();
        UUID tgt = UUID.randomUUID();

        doNothing().when(actionLinkService).deleteActionLink(src, tgt);

        mockMvc.perform(delete("/api/areas/" + areaId + "/links/" + src + "/" + tgt)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNoContent());

        verify(actionLinkService, times(1)).deleteActionLink(src, tgt);
    }

    // Test createActionLink - Success case
    @Test
    @DisplayName("createActionLink - Should create link successfully when user is authorized")
    void createActionLinkSuccessWhenAuthorized() throws Exception {
        CreateActionLinkRequest req = new CreateActionLinkRequest();
        req.setSourceActionInstanceId(UUID.randomUUID());
        req.setTargetActionInstanceId(UUID.randomUUID());

        Area area = new Area();
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        ActionLinkResponse response = new ActionLinkResponse();

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionLinkService.createActionLink(any(CreateActionLinkRequest.class), eq(areaId)))
                .thenReturn(response);

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/" + areaId + "/links")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isCreated());

        verify(actionLinkService, times(1)).createActionLink(any(CreateActionLinkRequest.class), eq(areaId));
    }

    // Test createActionLink - Exception handling
    @Test
    @DisplayName("createActionLink - Should return 500 when exception occurs")
    void createActionLinkReturnsInternalServerErrorOnException() throws Exception {
        CreateActionLinkRequest req = new CreateActionLinkRequest();
        req.setSourceActionInstanceId(UUID.randomUUID());
        req.setTargetActionInstanceId(UUID.randomUUID());

        Area area = new Area();
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionLinkService.createActionLink(any(CreateActionLinkRequest.class), eq(areaId)))
                .thenThrow(new RuntimeException("Service error"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/" + areaId + "/links")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError());
    }

    // Test createActionLinksBatch - Success case
    @Test
    @DisplayName("createActionLinksBatch - Should create batch links successfully when user is authorized")
    void createActionLinksBatchSuccessWhenAuthorized() throws Exception {
        BatchCreateActionLinksRequest req = new BatchCreateActionLinksRequest();
        ArrayList<BatchCreateActionLinksRequest.ActionLinkData> links = new ArrayList<>();
        
        BatchCreateActionLinksRequest.ActionLinkData link1 = new BatchCreateActionLinksRequest.ActionLinkData();
        link1.setSourceActionInstanceId(UUID.randomUUID());
        link1.setTargetActionInstanceId(UUID.randomUUID());
        links.add(link1);
        
        req.setLinks(links);

        Area area = new Area();
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        ActionLinkResponse response = new ActionLinkResponse();

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionLinkService.createActionLinksBatch(any(BatchCreateActionLinksRequest.class)))
                .thenReturn(List.of(response));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/" + areaId + "/links/batch")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0]").exists());

        verify(actionLinkService, times(1)).createActionLinksBatch(any(BatchCreateActionLinksRequest.class));
    }

    // Test createActionLinksBatch - Forbidden
    @Test
    @DisplayName("createActionLinksBatch - Should return 403 when user is not owner")
    void createActionLinksBatchForbiddenWhenUserNotOwner() throws Exception {
        BatchCreateActionLinksRequest req = new BatchCreateActionLinksRequest();
        ArrayList<BatchCreateActionLinksRequest.ActionLinkData> links = new ArrayList<>();
        
        BatchCreateActionLinksRequest.ActionLinkData link1 = new BatchCreateActionLinksRequest.ActionLinkData();
        link1.setSourceActionInstanceId(UUID.randomUUID());
        link1.setTargetActionInstanceId(UUID.randomUUID());
        links.add(link1);
        
        req.setLinks(links);

        Area area = new Area();
        User owner = new User();
        owner.setId(UUID.randomUUID()); // Different user ID
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/" + areaId + "/links/batch")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());

        verify(actionLinkService, never()).createActionLinksBatch(any());
    }

    // Test createActionLinksBatch - Exception handling
    @Test
    @DisplayName("createActionLinksBatch - Should return 500 when exception occurs")
    void createActionLinksBatchReturnsInternalServerErrorOnException() throws Exception {
        BatchCreateActionLinksRequest req = new BatchCreateActionLinksRequest();
        ArrayList<BatchCreateActionLinksRequest.ActionLinkData> links = new ArrayList<>();
        
        BatchCreateActionLinksRequest.ActionLinkData link1 = new BatchCreateActionLinksRequest.ActionLinkData();
        link1.setSourceActionInstanceId(UUID.randomUUID());
        link1.setTargetActionInstanceId(UUID.randomUUID());
        links.add(link1);
        
        req.setLinks(links);

        Area area = new Area();
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionLinkService.createActionLinksBatch(any(BatchCreateActionLinksRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/" + areaId + "/links/batch")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError());
    }

    // Test getActionLinksByArea - Forbidden
    @Test
    @DisplayName("getActionLinksByArea - Should return 403 when user is not owner")
    void getActionLinksByAreaForbiddenWhenUserNotOwner() throws Exception {
        Area area = new Area();
        User owner = new User();
        owner.setId(UUID.randomUUID()); // Different user ID
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        mockMvc.perform(get("/api/areas/" + areaId + "/links")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());

        verify(actionLinkService, never()).getActionLinksByArea(any());
    }

    // Test getActionLinksByArea - Exception handling
    @Test
    @DisplayName("getActionLinksByArea - Should return 500 when exception occurs")
    void getActionLinksByAreaReturnsInternalServerErrorOnException() throws Exception {
        Area area = new Area();
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(actionLinkService.getActionLinksByArea(areaId))
                .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(get("/api/areas/" + areaId + "/links")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError());
    }

    // Test deleteActionLink - Forbidden
    @Test
    @DisplayName("deleteActionLink - Should return 403 when user is not owner")
    void deleteActionLinkForbiddenWhenUserNotOwner() throws Exception {
        Area area = new Area();
        User owner = new User();
        owner.setId(UUID.randomUUID()); // Different user ID
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        UUID src = UUID.randomUUID();
        UUID tgt = UUID.randomUUID();

        mockMvc.perform(delete("/api/areas/" + areaId + "/links/" + src + "/" + tgt)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());

        verify(actionLinkService, never()).deleteActionLink(any(), any());
    }

    // Test deleteActionLink - Exception handling
    @Test
    @DisplayName("deleteActionLink - Should return 500 when exception occurs")
    void deleteActionLinkReturnsInternalServerErrorOnException() throws Exception {
        Area area = new Area();
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        UUID src = UUID.randomUUID();
        UUID tgt = UUID.randomUUID();

        doThrow(new RuntimeException("Service error")).when(actionLinkService).deleteActionLink(src, tgt);

        mockMvc.perform(delete("/api/areas/" + areaId + "/links/" + src + "/" + tgt)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError());
    }

    // Test deleteAllActionLinksByArea - Success
    @Test
    @DisplayName("deleteAllActionLinksByArea - Should delete all links successfully when user is authorized")
    void deleteAllActionLinksByAreaSuccessWhenAuthorized() throws Exception {
        Area area = new Area();
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        doNothing().when(actionLinkService).deleteActionLinksByArea(areaId);

        mockMvc.perform(delete("/api/areas/" + areaId + "/links")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNoContent());

        verify(actionLinkService, times(1)).deleteActionLinksByArea(areaId);
    }

    // Test deleteAllActionLinksByArea - Forbidden
    @Test
    @DisplayName("deleteAllActionLinksByArea - Should return 403 when user is not owner")
    void deleteAllActionLinksByAreaForbiddenWhenUserNotOwner() throws Exception {
        Area area = new Area();
        User owner = new User();
        owner.setId(UUID.randomUUID()); // Different user ID
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        mockMvc.perform(delete("/api/areas/" + areaId + "/links")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());

        verify(actionLinkService, never()).deleteActionLinksByArea(any());
    }

    // Test deleteAllActionLinksByArea - Exception handling
    @Test
    @DisplayName("deleteAllActionLinksByArea - Should return 500 when exception occurs")
    void deleteAllActionLinksByAreaReturnsInternalServerErrorOnException() throws Exception {
        Area area = new Area();
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        doThrow(new RuntimeException("Service error")).when(actionLinkService).deleteActionLinksByArea(areaId);

        mockMvc.perform(delete("/api/areas/" + areaId + "/links")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError());
    }

    // Test getUserIdFromRequest - Missing token (no cookies)
    @Test
    @DisplayName("createActionLink - Should return 500 when access token is missing (no cookies)")
    void createActionLinkReturnsInternalServerErrorWhenNoCookies() throws Exception {
        CreateActionLinkRequest req = new CreateActionLinkRequest();
        req.setSourceActionInstanceId(UUID.randomUUID());
        req.setTargetActionInstanceId(UUID.randomUUID());

        String body = objectMapper.writeValueAsString(req);

        // No cookie provided
        mockMvc.perform(post("/api/areas/" + areaId + "/links")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isInternalServerError());
    }

    // Test canUserAccessArea - Area not found
    @Test
    @DisplayName("createActionLink - Should return 403 when area is not found")
    void createActionLinkForbiddenWhenAreaNotFound() throws Exception {
        CreateActionLinkRequest req = new CreateActionLinkRequest();
        req.setSourceActionInstanceId(UUID.randomUUID());
        req.setTargetActionInstanceId(UUID.randomUUID());

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.empty());

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/" + areaId + "/links")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());

        verify(actionLinkService, never()).createActionLink(any(), any());
    }

    // Test canUserAccessArea - Exception during area check
    @Test
    @DisplayName("createActionLink - Should return 403 when exception occurs during area access check")
    void createActionLinkForbiddenWhenExceptionDuringAreaCheck() throws Exception {
        CreateActionLinkRequest req = new CreateActionLinkRequest();
        req.setSourceActionInstanceId(UUID.randomUUID());
        req.setTargetActionInstanceId(UUID.randomUUID());

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenThrow(new RuntimeException("Database error"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/" + areaId + "/links")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());

        verify(actionLinkService, never()).createActionLink(any(), any());
    }

    // Test getUserIdFromRequest - Exception extracting user ID
    @Test
    @DisplayName("createActionLink - Should return 500 when JWT extraction fails")
    void createActionLinkReturnsInternalServerErrorWhenJwtExtractionFails() throws Exception {
        CreateActionLinkRequest req = new CreateActionLinkRequest();
        req.setSourceActionInstanceId(UUID.randomUUID());
        req.setTargetActionInstanceId(UUID.randomUUID());

        when(jwtService.extractUserIdFromAccessToken(anyString()))
                .thenThrow(new RuntimeException("Invalid token"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/" + areaId + "/links")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError());
    }
}
