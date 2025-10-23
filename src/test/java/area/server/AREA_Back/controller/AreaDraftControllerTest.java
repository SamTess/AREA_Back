package area.server.AREA_Back.controller;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.AreaDraftRequest;
import area.server.AREA_Back.dto.AreaDraftResponse;
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
}
