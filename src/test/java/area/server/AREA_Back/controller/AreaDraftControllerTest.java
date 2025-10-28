package area.server.AREA_Back.controller;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.AreaDraftRequest;
import area.server.AREA_Back.dto.AreaDraftResponse;
import area.server.AREA_Back.dto.AreaResponse;
import area.server.AREA_Back.dto.CreateAreaWithActionsAndLinksRequest;
import area.server.AREA_Back.service.Area.AreaDraftCacheService;
import area.server.AREA_Back.service.Area.AreaService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AreaDraftController Tests")
class AreaDraftControllerTest {

    @Mock
    private AreaDraftCacheService draftCacheService;

    @Mock
    private AreaService areaService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AreaDraftController areaDraftController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(areaDraftController).build();
        objectMapper = new ObjectMapper();
        userId = UUID.randomUUID();
    }

    @Test
    void saveDraftSuccess() throws Exception {
        AreaDraftRequest request = new AreaDraftRequest();
        request.setName("Test Draft");
        
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.saveDraft(eq(userId), any(AreaDraftRequest.class), any())).thenReturn(draftId);

        mockMvc.perform(post("/api/areas/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value(draftId));

        verify(draftCacheService).saveDraft(eq(userId), any(AreaDraftRequest.class), any());
    }

    @Test
    void saveDraftWithAreaId() throws Exception {
        AreaDraftRequest request = new AreaDraftRequest();
        request.setName("Test Draft");
        
        UUID areaId = UUID.randomUUID();
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.saveDraft(eq(userId), any(AreaDraftRequest.class), eq(areaId))).thenReturn(draftId);

        mockMvc.perform(post("/api/areas/drafts")
                        .param("areaId", areaId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value(draftId));
    }

    @Test
    void saveDraftCacheUnavailable() throws Exception {
        AreaDraftRequest request = new AreaDraftRequest();
        request.setName("Test Draft");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(false);

        mockMvc.perform(post("/api/areas/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Cache service unavailable"));
    }

    @Test
    void saveDraftFailure() throws Exception {
        AreaDraftRequest request = new AreaDraftRequest();
        request.setName("Test Draft");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.saveDraft(eq(userId), any(AreaDraftRequest.class), any()))
                .thenThrow(new RuntimeException("Cache error"));

        mockMvc.perform(post("/api/areas/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Failed to save draft"));
    }

    @Test
    void getUserDraftsSuccess() throws Exception {
        AreaDraftResponse draft = new AreaDraftResponse();
        draft.setDraftId(UUID.randomUUID().toString());
        draft.setName("Test Draft");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getUserDrafts(userId)).thenReturn(List.of(draft));

        mockMvc.perform(get("/api/areas/drafts")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].draftId").value(draft.getDraftId()));

        verify(draftCacheService).getUserDrafts(userId);
    }

    @Test
    void getUserDraftsCacheUnavailable() throws Exception {
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/areas/drafts")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Cache service unavailable"));
    }

    @Test
    void getDraftByIdSuccess() throws Exception {
        String draftId = UUID.randomUUID().toString();
        AreaDraftResponse draft = new AreaDraftResponse();
        draft.setDraftId(draftId);
        draft.setName("Test Draft");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getDraft(userId, draftId)).thenReturn(java.util.Optional.of(draft));

        mockMvc.perform(get("/api/areas/drafts/" + draftId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value(draftId));
    }

    @Test
    void getDraftByIdNotFound() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getDraft(userId, draftId)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/areas/drafts/" + draftId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDraftSuccess() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        doNothing().when(draftCacheService).deleteDraft(userId, draftId);

        mockMvc.perform(delete("/api/areas/drafts/" + draftId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNoContent());

        verify(draftCacheService).deleteDraft(userId, draftId);
    }

    @Test
    void getEditDraftSuccess() throws Exception {
        UUID areaId = UUID.randomUUID();
        AreaDraftResponse draft = new AreaDraftResponse();
        draft.setName("Edit Draft");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getEditDraft(userId, areaId)).thenReturn(java.util.Optional.of(draft));

        mockMvc.perform(get("/api/areas/drafts/edit/" + areaId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk());
    }

    @Test
    void getEditDraftNotFound() throws Exception {
        UUID areaId = UUID.randomUUID();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getEditDraft(userId, areaId)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/areas/drafts/edit/" + areaId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void saveDraftInvalidAreaIdFormat() throws Exception {
        AreaDraftRequest request = new AreaDraftRequest();
        request.setName("Test Draft");
        
        String invalidAreaId = "invalid-uuid";
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.saveDraft(eq(userId), any(AreaDraftRequest.class), isNull())).thenReturn(draftId);

        mockMvc.perform(post("/api/areas/drafts")
                        .param("areaId", invalidAreaId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value(draftId));
    }

    @Test
    void saveDraftGeneralException() throws Exception {
        // Test with invalid JSON in request which Spring handles before reaching controller
        mockMvc.perform(post("/api/areas/drafts")
                        .contentType("application/json")
                        .content("invalid json content {{{")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isBadRequest()); // Spring handles invalid JSON as bad request
    }

    @Test
    void getUserDraftsException() throws Exception {
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getUserDrafts(userId)).thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/api/areas/drafts")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to retrieve drafts"));
    }

    @Test
    void getEditDraftInvalidUUID() throws Exception {
        String invalidAreaId = "invalid-uuid";
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/areas/drafts/edit/" + invalidAreaId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid area ID format"));
    }

    @Test
    void getEditDraftGeneralException() throws Exception {
        UUID areaId = UUID.randomUUID();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getEditDraft(userId, areaId)).thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(get("/api/areas/drafts/edit/" + areaId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to retrieve edit draft"));
    }

    @Test
    void getDraftException() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getDraft(userId, draftId)).thenThrow(new RuntimeException("Cache error"));

        mockMvc.perform(get("/api/areas/drafts/" + draftId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to retrieve draft"));
    }

    @Test
    void deleteDraftRuntimeException() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        doThrow(new RuntimeException("Delete failed")).when(draftCacheService).deleteDraft(userId, draftId);

        mockMvc.perform(delete("/api/areas/drafts/" + draftId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Failed to delete draft"));
    }

    @Test
    void deleteDraftGeneralException() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new IllegalStateException("Unexpected"));

        mockMvc.perform(delete("/api/areas/drafts/" + draftId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Failed to delete draft"));
    }

    @Test
    void commitDraftSuccess() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        AreaDraftResponse draft = new AreaDraftResponse();
        draft.setDraftId(draftId);
        draft.setName("Test Draft");
        draft.setDescription("Description");
        
        AreaResponse areaResponse = new AreaResponse();
        areaResponse.setId(UUID.randomUUID());
        areaResponse.setName("Test Draft");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getDraft(userId, draftId)).thenReturn(java.util.Optional.of(draft));
        when(areaService.createAreaWithActionsAndLinks(any(CreateAreaWithActionsAndLinksRequest.class))).thenReturn(areaResponse);
        doNothing().when(draftCacheService).deleteDraft(userId, draftId);

        mockMvc.perform(post("/api/areas/drafts/" + draftId + "/commit")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Draft"));

        verify(areaService).createAreaWithActionsAndLinks(any(CreateAreaWithActionsAndLinksRequest.class));
        verify(draftCacheService).deleteDraft(userId, draftId);
    }

    @Test
    void commitDraftWithConnections() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        AreaDraftResponse draft = new AreaDraftResponse();
        draft.setDraftId(draftId);
        draft.setName("Test Draft");
        draft.setDescription("Description");
        
        List<AreaDraftRequest.ConnectionRequest> connections = new ArrayList<>();
        AreaDraftRequest.ConnectionRequest connection = new AreaDraftRequest.ConnectionRequest();
        connection.setSourceServiceId(UUID.randomUUID().toString());
        connection.setTargetServiceId(UUID.randomUUID().toString());
        connection.setLinkType("data");
        connection.setOrder(1);
        connections.add(connection);
        draft.setConnections(connections);
        
        AreaResponse areaResponse = new AreaResponse();
        areaResponse.setId(UUID.randomUUID());
        areaResponse.setName("Test Draft");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getDraft(userId, draftId)).thenReturn(java.util.Optional.of(draft));
        when(areaService.createAreaWithActionsAndLinks(any(CreateAreaWithActionsAndLinksRequest.class))).thenReturn(areaResponse);
        doNothing().when(draftCacheService).deleteDraft(userId, draftId);

        mockMvc.perform(post("/api/areas/drafts/" + draftId + "/commit")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Draft"));

        verify(areaService).createAreaWithActionsAndLinks(any(CreateAreaWithActionsAndLinksRequest.class));
    }

    @Test
    void commitDraftCacheUnavailable() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(false);

        mockMvc.perform(post("/api/areas/drafts/" + draftId + "/commit")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Cache service unavailable"));
    }

    @Test
    void commitDraftNotFound() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getDraft(userId, draftId)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/api/areas/drafts/" + draftId + "/commit")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Draft not found"));
    }

    @Test
    void commitDraftDeleteFailure() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        AreaDraftResponse draft = new AreaDraftResponse();
        draft.setDraftId(draftId);
        draft.setName("Test Draft");
        
        AreaResponse areaResponse = new AreaResponse();
        areaResponse.setId(UUID.randomUUID());
        areaResponse.setName("Test Draft");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getDraft(userId, draftId)).thenReturn(java.util.Optional.of(draft));
        when(areaService.createAreaWithActionsAndLinks(any(CreateAreaWithActionsAndLinksRequest.class))).thenReturn(areaResponse);
        doThrow(new RuntimeException("Delete failed")).when(draftCacheService).deleteDraft(userId, draftId);

        mockMvc.perform(post("/api/areas/drafts/" + draftId + "/commit")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Draft"));
    }

    @Test
    void commitDraftIllegalArgumentException() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        AreaDraftResponse draft = new AreaDraftResponse();
        draft.setDraftId(draftId);
        draft.setName("Test Draft");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getDraft(userId, draftId)).thenReturn(java.util.Optional.of(draft));
        when(areaService.createAreaWithActionsAndLinks(any(CreateAreaWithActionsAndLinksRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid data"));

        mockMvc.perform(post("/api/areas/drafts/" + draftId + "/commit")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid draft data"));
    }

    @Test
    void commitDraftRuntimeException() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        AreaDraftResponse draft = new AreaDraftResponse();
        draft.setDraftId(draftId);
        draft.setName("Test Draft");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        when(draftCacheService.getDraft(userId, draftId)).thenReturn(java.util.Optional.of(draft));
        when(areaService.createAreaWithActionsAndLinks(any(CreateAreaWithActionsAndLinksRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(post("/api/areas/drafts/" + draftId + "/commit")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Failed to commit draft"));
    }

    @Test
    void commitDraftGeneralException() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new IllegalStateException("Unexpected"));

        mockMvc.perform(post("/api/areas/drafts/" + draftId + "/commit")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Failed to commit draft"));
    }

    @Test
    void extendDraftTTLSuccess() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        doNothing().when(draftCacheService).extendDraftTTL(userId, draftId);

        mockMvc.perform(patch("/api/areas/drafts/" + draftId + "/extend")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Draft TTL extended successfully"));

        verify(draftCacheService).extendDraftTTL(userId, draftId);
    }

    @Test
    void extendDraftTTLCacheUnavailable() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(false);

        mockMvc.perform(patch("/api/areas/drafts/" + draftId + "/extend")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Cache service unavailable"));
    }

    @Test
    void extendDraftTTLException() throws Exception {
        String draftId = UUID.randomUUID().toString();
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(draftCacheService.isCacheAvailable()).thenReturn(true);
        doThrow(new RuntimeException("Extension failed")).when(draftCacheService).extendDraftTTL(userId, draftId);

        mockMvc.perform(patch("/api/areas/drafts/" + draftId + "/extend")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to extend draft TTL"));
    }

    @Test
    void getUserIdFromRequestNoCookies() throws Exception {
        AreaDraftRequest request = new AreaDraftRequest();
        request.setName("Test Draft");

        mockMvc.perform(post("/api/areas/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Failed to save draft"));
    }

    @Test
    void getUserIdFromRequestExtractionFailure() throws Exception {
        AreaDraftRequest request = new AreaDraftRequest();
        request.setName("Test Draft");
        
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Token invalid"));

        mockMvc.perform(post("/api/areas/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Failed to save draft"));
    }
}
