package area.server.AREA_Back.controller;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.AreaActionRequest;
import area.server.AREA_Back.dto.AreaResponse;
import area.server.AREA_Back.dto.CreateAreaRequest;
import area.server.AREA_Back.dto.CreateAreaWithActionsAndLinksRequest;
import area.server.AREA_Back.dto.CreateAreaWithActionsRequest;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.CronSchedulerService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AreaController Tests")
class AreaControllerTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AreaService areaService;

    @Mock
    private CronSchedulerService cronSchedulerService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AreaController areaController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.findAndRegisterModules();
        
        mockMvc = MockMvcBuilders.standaloneSetup(areaController)
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        userId = UUID.randomUUID();
    }

    @Test
    void getAllAreasUnauthorizedWhenNoCookie() throws Exception {
        // No cookies, getUserIdFromRequest will fail and controller catches it, returns 401
        mockMvc.perform(get("/api/areas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAreaByIdForbiddenWhenNotOwner() throws Exception {
        UUID areaId = UUID.randomUUID();
        Area area = new Area();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        mockMvc.perform(get("/api/areas/" + areaId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAreaSuccess() throws Exception {
        CreateAreaRequest req = new CreateAreaRequest();
        req.setName("MyArea");
        req.setDescription("desc");
        req.setUserId(userId); // Required field for validation

        Area saved = new Area();
        saved.setId(UUID.randomUUID());
        saved.setName("MyArea");

        AreaResponse resp = new AreaResponse();
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(areaRepository.save(any(Area.class))).thenReturn(saved);
        when(areaService.convertToResponse(saved)).thenReturn(resp);

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas").contentType("application/json").content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isCreated());
    }

    @Test
    void toggleAreaNotFound() throws Exception {
        UUID areaId = UUID.randomUUID();
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/areas/" + areaId + "/toggle")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void toggleAreaSuccess() throws Exception {
        UUID areaId = UUID.randomUUID();
        Area area = new Area();
        area.setId(areaId);
        area.setEnabled(false);
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        Area toggled = new Area();
        toggled.setId(areaId);
        toggled.setEnabled(true);
        toggled.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(areaRepository.save(any(Area.class))).thenReturn(toggled);
        when(areaService.convertToResponse(any(Area.class))).thenReturn(new AreaResponse());

        mockMvc.perform(patch("/api/areas/" + areaId + "/toggle")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk());

        verify(areaRepository).save(any(Area.class));
    }

    @Test
    void deleteAreaSuccess() throws Exception {
        UUID areaId = UUID.randomUUID();
        Area area = new Area();
        area.setId(areaId);
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        doNothing().when(areaRepository).deleteById(areaId);

        mockMvc.perform(delete("/api/areas/" + areaId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNoContent());

        verify(areaRepository).deleteById(areaId);
    }

    @Test
    void deleteAreaNotFound() throws Exception {
        UUID areaId = UUID.randomUUID();
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/areas/" + areaId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void triggerAreaManuallySuccess() throws Exception {
        UUID areaId = UUID.randomUUID();
        Area area = new Area();
        area.setId(areaId);
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        Map<String, Object> result = Map.of("status", "triggered", "executionId", UUID.randomUUID().toString());

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(areaService.triggerAreaManually(eq(areaId), any())).thenReturn(result);

        mockMvc.perform(post("/api/areas/" + areaId + "/trigger")
                        .contentType("application/json")
                        .content("{\"data\":\"test\"}")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("triggered"));

        verify(areaService).triggerAreaManually(eq(areaId), any());
    }

    @Test
    void triggerAreaForbiddenWhenNotOwner() throws Exception {
        UUID areaId = UUID.randomUUID();
        Area area = new Area();
        area.setId(areaId);
        User owner = new User();
        owner.setId(UUID.randomUUID()); // Different user
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        mockMvc.perform(post("/api/areas/" + areaId + "/trigger")
                        .contentType("application/json")
                        .content("{}")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void triggerAreaNotFound() throws Exception {
        UUID areaId = UUID.randomUUID();
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/areas/" + areaId + "/trigger")
                        .contentType("application/json")
                        .content("{}")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSchedulerStatus() throws Exception {
        Map<UUID, Boolean> status = Map.of(UUID.randomUUID(), true);
        when(cronSchedulerService.getScheduledTasksStatus()).thenReturn(status);

        mockMvc.perform(get("/api/areas/scheduler/status"))
                .andExpect(status().isOk());

        verify(cronSchedulerService).getScheduledTasksStatus();
    }

    @Test
    void getAllAreasSuccess() throws Exception {
        Area area = new Area();
        area.setId(UUID.randomUUID());
        area.setName("Test Area");
        
        AreaResponse areaResponse = new AreaResponse();
        areaResponse.setId(area.getId());
        areaResponse.setName(area.getName());

        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(area), pageable, 1));
        when(areaService.convertToResponse(any(Area.class))).thenReturn(areaResponse);

        mockMvc.perform(get("/api/areas")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk());

        verify(areaRepository).findByUserId(eq(userId), any(Pageable.class));
    }

    @Test
    void getAllAreasWithCustomPagination() throws Exception {
        Area area = new Area();
        area.setId(UUID.randomUUID());
        area.setName("Test Area");
        
        AreaResponse areaResponse = new AreaResponse();
        areaResponse.setId(area.getId());
        areaResponse.setName(area.getName());

        Pageable pageable = org.springframework.data.domain.PageRequest.of(1, 10, org.springframework.data.domain.Sort.by("name").descending());

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(area), pageable, 1));
        when(areaService.convertToResponse(any(Area.class))).thenReturn(areaResponse);

        mockMvc.perform(get("/api/areas")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sortBy", "name")
                        .param("sortDir", "desc")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk());
    }

    @Test
    void getAreaByIdSuccess() throws Exception {
        UUID areaId = UUID.randomUUID();
        Area area = new Area();
        area.setId(areaId);
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        AreaResponse areaResponse = new AreaResponse();

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(areaService.convertToResponse(area)).thenReturn(areaResponse);

        mockMvc.perform(get("/api/areas/" + areaId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk());
    }

    @Test
    void getAreaByIdNotFound() throws Exception {
        UUID areaId = UUID.randomUUID();

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/areas/" + areaId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAreasByUserIdSuccess() throws Exception {
        Area area1 = new Area();
        area1.setId(UUID.randomUUID());
        area1.setName("Area 1");

        Area area2 = new Area();
        area2.setId(UUID.randomUUID());
        area2.setName("Area 2");

        List<Area> areas = Arrays.asList(area1, area2);
        AreaResponse response = new AreaResponse();

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findByUserId(userId)).thenReturn(areas);
        when(areaService.convertToResponse(any(Area.class))).thenReturn(response);

        mockMvc.perform(get("/api/areas/user/" + userId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk());

        verify(areaRepository).findByUserId(userId);
    }

    @Test
    void getAreasByUserIdForbiddenWhenDifferentUser() throws Exception {
        UUID differentUserId = UUID.randomUUID();

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);

        mockMvc.perform(get("/api/areas/user/" + differentUserId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAreasByUserIdUnauthorized() throws Exception {
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Invalid token"));

        mockMvc.perform(get("/api/areas/user/" + userId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchAreasSuccess() throws Exception {
        Area area1 = new Area();
        area1.setName("Test Area");
        Area area2 = new Area();
        area2.setName("Another Test");
        Area area3 = new Area();
        area3.setName("Different");

        List<Area> allAreas = Arrays.asList(area1, area2, area3);
        AreaResponse response = new AreaResponse();

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findByUserId(userId)).thenReturn(allAreas);
        when(areaService.convertToResponse(any(Area.class))).thenReturn(response);

        mockMvc.perform(get("/api/areas/search")
                        .param("name", "test")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk());

        verify(areaRepository).findByUserId(userId);
    }

    @Test
    void searchAreasUnauthorized() throws Exception {
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Invalid token"));

        mockMvc.perform(get("/api/areas/search")
                        .param("name", "test")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAreaUnauthorizedWhenUserNotFound() throws Exception {
        CreateAreaRequest req = new CreateAreaRequest();
        req.setName("MyArea");
        req.setDescription("desc");
        req.setUserId(userId);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAreaUnauthorizedOnException() throws Exception {
        CreateAreaRequest req = new CreateAreaRequest();
        req.setName("MyArea");
        req.setDescription("desc");
        req.setUserId(userId);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Token error"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAreaWithActionsSuccess() throws Exception {
        CreateAreaWithActionsRequest req = new CreateAreaWithActionsRequest();
        req.setName("Area with Actions");
        req.setDescription("desc");
        
        // Add at least one action (required)
        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test Action");
        action.setServiceAccountId(UUID.randomUUID());
        req.setActions(List.of(action));

        AreaResponse response = new AreaResponse();
        response.setName("Area with Actions");

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaService.createAreaWithActions(any(CreateAreaWithActionsRequest.class))).thenReturn(response);

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/with-actions")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Area with Actions"));

        verify(areaService).createAreaWithActions(any(CreateAreaWithActionsRequest.class));
    }

    @Test
    void createAreaWithActionsBadRequest() throws Exception {
        CreateAreaWithActionsRequest req = new CreateAreaWithActionsRequest();
        req.setName("Area");
        
        // Add at least one action (required)
        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test Action");
        action.setServiceAccountId(UUID.randomUUID());
        req.setActions(List.of(action));

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaService.createAreaWithActions(any(CreateAreaWithActionsRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid action definition"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/with-actions")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.message").value("Invalid action definition"));
    }

    @Test
    void createAreaWithActionsInternalError() throws Exception {
        CreateAreaWithActionsRequest req = new CreateAreaWithActionsRequest();
        req.setName("Area");
        
        // Add at least one action (required)
        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test Action");
        action.setServiceAccountId(UUID.randomUUID());
        req.setActions(List.of(action));

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaService.createAreaWithActions(any(CreateAreaWithActionsRequest.class)))
                .thenThrow(new RuntimeException("Database error"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/with-actions")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void createAreaWithActionsAndLinksSuccess() throws Exception {
        CreateAreaWithActionsAndLinksRequest req = new CreateAreaWithActionsAndLinksRequest();
        req.setName("Area with Links");
        
        // Add at least one action (required)
        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test Action");
        action.setServiceAccountId(UUID.randomUUID());
        req.setActions(List.of(action));

        AreaResponse response = new AreaResponse();
        response.setName("Area with Links");

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaService.createAreaWithActionsAndLinks(any(CreateAreaWithActionsAndLinksRequest.class)))
                .thenReturn(response);

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/with-links")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Area with Links"));

        verify(areaService).createAreaWithActionsAndLinks(any(CreateAreaWithActionsAndLinksRequest.class));
    }

    @Test
    void createAreaWithActionsAndLinksBadRequest() throws Exception {
        CreateAreaWithActionsAndLinksRequest req = new CreateAreaWithActionsAndLinksRequest();
        req.setName("Area");
        
        // Add at least one action (required)
        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test Action");
        action.setServiceAccountId(UUID.randomUUID());
        req.setActions(List.of(action));

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaService.createAreaWithActionsAndLinks(any(CreateAreaWithActionsAndLinksRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid links"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/with-links")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isBadRequest());
        
        verify(areaService).createAreaWithActionsAndLinks(any(CreateAreaWithActionsAndLinksRequest.class));
    }

    @Test
    void createAreaWithActionsAndLinksUnauthorized() throws Exception {
        CreateAreaWithActionsAndLinksRequest req = new CreateAreaWithActionsAndLinksRequest();
        req.setName("Area");
        
        // Add at least one action (required)
        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test Action");
        action.setServiceAccountId(UUID.randomUUID());
        req.setActions(List.of(action));

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Invalid token"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/areas/with-links")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateAreaSuccess() throws Exception {
        UUID areaId = UUID.randomUUID();
        CreateAreaRequest req = new CreateAreaRequest();
        req.setName("Updated Area");
        req.setDescription("Updated desc");
        req.setUserId(userId);

        Area area = new Area();
        area.setId(areaId);
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        Area updated = new Area();
        updated.setId(areaId);
        updated.setName("Updated Area");
        updated.setUser(owner);

        AreaResponse response = new AreaResponse();

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(areaRepository.save(any(Area.class))).thenReturn(updated);
        when(areaService.convertToResponse(any(Area.class))).thenReturn(response);

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(put("/api/areas/" + areaId)
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk());

        verify(areaRepository).save(any(Area.class));
    }

    @Test
    void updateAreaNotFound() throws Exception {
        UUID areaId = UUID.randomUUID();
        CreateAreaRequest req = new CreateAreaRequest();
        req.setName("Updated");
        req.setUserId(userId);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.empty());

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(put("/api/areas/" + areaId)
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAreaForbidden() throws Exception {
        UUID areaId = UUID.randomUUID();
        CreateAreaRequest req = new CreateAreaRequest();
        req.setName("Updated");
        req.setUserId(userId);

        Area area = new Area();
        area.setId(areaId);
        User owner = new User();
        owner.setId(UUID.randomUUID()); // Different user
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(put("/api/areas/" + areaId)
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAreaUnauthorized() throws Exception {
        UUID areaId = UUID.randomUUID();
        CreateAreaRequest req = new CreateAreaRequest();
        req.setName("Updated");
        req.setUserId(userId);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Invalid token"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(put("/api/areas/" + areaId)
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateAreaCompleteSuccess() throws Exception {
        UUID areaId = UUID.randomUUID();
        CreateAreaWithActionsAndLinksRequest req = new CreateAreaWithActionsAndLinksRequest();
        req.setName("Complete Update");
        
        // Add at least one action (required)
        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test Action");
        action.setServiceAccountId(UUID.randomUUID());
        req.setActions(List.of(action));

        AreaResponse response = new AreaResponse();
        response.setName("Complete Update");

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaService.updateAreaWithActionsAndLinks(eq(areaId), any(CreateAreaWithActionsAndLinksRequest.class)))
                .thenReturn(response);

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(put("/api/areas/" + areaId + "/complete")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Complete Update"));

        verify(areaService).updateAreaWithActionsAndLinks(eq(areaId), any(CreateAreaWithActionsAndLinksRequest.class));
    }

    @Test
    void updateAreaCompleteBadRequest() throws Exception {
        UUID areaId = UUID.randomUUID();
        CreateAreaWithActionsAndLinksRequest req = new CreateAreaWithActionsAndLinksRequest();
        req.setName("Update");
        
        // Add at least one action (required)
        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test Action");
        action.setServiceAccountId(UUID.randomUUID());
        req.setActions(List.of(action));

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaService.updateAreaWithActionsAndLinks(eq(areaId), any(CreateAreaWithActionsAndLinksRequest.class)))
                .thenThrow(new IllegalArgumentException("Validation error"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(put("/api/areas/" + areaId + "/complete")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.message").value("Validation error"));
    }

    @Test
    void updateAreaCompleteInternalError() throws Exception {
        UUID areaId = UUID.randomUUID();
        CreateAreaWithActionsAndLinksRequest req = new CreateAreaWithActionsAndLinksRequest();
        req.setName("Update");
        
        // Add at least one action (required)
        AreaActionRequest action = new AreaActionRequest();
        action.setActionDefinitionId(UUID.randomUUID());
        action.setName("Test Action");
        action.setServiceAccountId(UUID.randomUUID());
        req.setActions(List.of(action));

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaService.updateAreaWithActionsAndLinks(eq(areaId), any(CreateAreaWithActionsAndLinksRequest.class)))
                .thenThrow(new RuntimeException("Database error"));

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(put("/api/areas/" + areaId + "/complete")
                        .contentType("application/json")
                        .content(body)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void deleteAreaForbidden() throws Exception {
        UUID areaId = UUID.randomUUID();
        Area area = new Area();
        area.setId(areaId);
        User owner = new User();
        owner.setId(UUID.randomUUID()); // Different user
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        mockMvc.perform(delete("/api/areas/" + areaId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAreaUnauthorized() throws Exception {
        UUID areaId = UUID.randomUUID();
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Invalid token"));

        mockMvc.perform(delete("/api/areas/" + areaId)
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void toggleAreaForbidden() throws Exception {
        UUID areaId = UUID.randomUUID();
        Area area = new Area();
        area.setId(areaId);
        User owner = new User();
        owner.setId(UUID.randomUUID()); // Different user
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

        mockMvc.perform(patch("/api/areas/" + areaId + "/toggle")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void toggleAreaUnauthorized() throws Exception {
        UUID areaId = UUID.randomUUID();
        when(jwtService.extractUserIdFromAccessToken(anyString())).thenThrow(new RuntimeException("Invalid token"));

        mockMvc.perform(patch("/api/areas/" + areaId + "/toggle")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void triggerAreaBadRequest() throws Exception {
        UUID areaId = UUID.randomUUID();
        Area area = new Area();
        area.setId(areaId);
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(areaService.triggerAreaManually(eq(areaId), any()))
                .thenThrow(new IllegalArgumentException("AREA is disabled"));

        mockMvc.perform(post("/api/areas/" + areaId + "/trigger")
                        .contentType("application/json")
                        .content("{}")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request"));
    }

    @Test
    void triggerAreaInternalError() throws Exception {
        UUID areaId = UUID.randomUUID();
        Area area = new Area();
        area.setId(areaId);
        User owner = new User();
        owner.setId(userId);
        area.setUser(owner);

        when(jwtService.extractUserIdFromAccessToken(anyString())).thenReturn(userId);
        when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(areaService.triggerAreaManually(eq(areaId), any()))
                .thenThrow(new RuntimeException("Execution error"));

        mockMvc.perform(post("/api/areas/" + areaId + "/trigger")
                        .contentType("application/json")
                        .content("{}")
                        .cookie(new Cookie(AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME, "token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    @Test
    void reloadSchedulerSuccess() throws Exception {
        Map<UUID, Boolean> status = new HashMap<>();
        status.put(UUID.randomUUID(), true);

        when(cronSchedulerService.getScheduledTasksStatus()).thenReturn(status);
        when(cronSchedulerService.getActiveTasksCount()).thenReturn(1);
        doNothing().when(cronSchedulerService).reloadAllCronActivations();

        mockMvc.perform(post("/api/areas/scheduler/reload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("CRON scheduler reloaded successfully"))
                .andExpect(jsonPath("$.active_tasks_count").value(1));

        verify(cronSchedulerService).reloadAllCronActivations();
    }

    @Test
    void reloadSchedulerFailure() throws Exception {
        doThrow(new RuntimeException("Scheduler error")).when(cronSchedulerService).reloadAllCronActivations();

        mockMvc.perform(post("/api/areas/scheduler/reload"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to reload scheduler"));
    }

    @Test
    void getSchedulerStatusFailure() throws Exception {
        when(cronSchedulerService.getScheduledTasksStatus()).thenThrow(new RuntimeException("Status error"));

        mockMvc.perform(get("/api/areas/scheduler/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to get scheduler status"));
    }

    @Test
    void extractAccessTokenFromCookiesNoCookies() throws Exception {
        // No cookies means getUserIdFromRequest will fail
        mockMvc.perform(get("/api/areas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void extractAccessTokenFromCookiesWrongCookie() throws Exception {
        // Wrong cookie name
        mockMvc.perform(get("/api/areas")
                        .cookie(new Cookie("wrong_cookie", "token")))
                .andExpect(status().isUnauthorized());
    }
}
