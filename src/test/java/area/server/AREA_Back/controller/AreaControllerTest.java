package area.server.AREA_Back.controller;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.AreaResponse;
import area.server.AREA_Back.dto.CreateAreaRequest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
    private ObjectMapper objectMapper = new ObjectMapper();

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(areaController).build();
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
}
