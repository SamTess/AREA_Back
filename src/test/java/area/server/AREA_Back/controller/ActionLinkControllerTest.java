package area.server.AREA_Back.controller;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.ActionLinkResponse;
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
}
