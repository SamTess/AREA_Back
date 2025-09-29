package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.CreateAreaRequest;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AreaController.class)
@WithMockUser
class AreaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AreaRepository areaRepository;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private Area testArea;
    private CreateAreaRequest createAreaRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setIsActive(true);
        testUser.setIsAdmin(false);

        testArea = new Area();
        testArea.setId(UUID.randomUUID());
        testArea.setUser(testUser);
        testArea.setName("Test Area");
        testArea.setDescription("Test Description");
        testArea.setEnabled(true);
        testArea.setCreatedAt(LocalDateTime.now());
        testArea.setUpdatedAt(LocalDateTime.now());

        createAreaRequest = new CreateAreaRequest();
        createAreaRequest.setName("New Area");
        createAreaRequest.setDescription("New Description");
        createAreaRequest.setUserId(testUser.getId());
    }

    @Test
    void testGetAllAreas() throws Exception {
        Area area1 = new Area();
        area1.setId(UUID.randomUUID());
        area1.setUser(testUser);
        area1.setName("Area 1");
        area1.setEnabled(true);

        Area area2 = new Area();
        area2.setId(UUID.randomUUID());
        area2.setUser(testUser);
        area2.setName("Area 2");
        area2.setEnabled(false);

        Page<Area> areaPage = new PageImpl<>(Arrays.asList(area1, area2));
        
        when(areaRepository.findAll(any(PageRequest.class))).thenReturn(areaPage);

        mockMvc.perform(get("/api/areas")
                .param("page", "0")
                .param("size", "20")
                .param("sortBy", "name")
                .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Area 1"))
                .andExpect(jsonPath("$.content[1].name").value("Area 2"));

        verify(areaRepository).findAll(PageRequest.of(0, 20, Sort.by("name").ascending()));
    }

    @Test
    void testGetAreaById() throws Exception {
        when(areaRepository.findById(testArea.getId())).thenReturn(Optional.of(testArea));

        mockMvc.perform(get("/api/areas/{id}", testArea.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testArea.getId().toString()))
                .andExpect(jsonPath("$.name").value("Test Area"))
                .andExpect(jsonPath("$.description").value("Test Description"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.userId").value(testUser.getId().toString()));

        verify(areaRepository).findById(testArea.getId());
    }

    @Test
    void testGetAreaByIdNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(areaRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/areas/{id}", nonExistentId))
                .andExpect(status().isNotFound());

        verify(areaRepository).findById(nonExistentId);
    }

    @Test
    void testGetAreasByUserId() throws Exception {
        Area userArea1 = new Area();
        userArea1.setId(UUID.randomUUID());
        userArea1.setUser(testUser);
        userArea1.setName("User Area 1");
        userArea1.setEnabled(true);

        Area userArea2 = new Area();
        userArea2.setId(UUID.randomUUID());
        userArea2.setUser(testUser);
        userArea2.setName("User Area 2");
        userArea2.setEnabled(false);

        List<Area> userAreas = Arrays.asList(userArea1, userArea2);
        when(areaRepository.findByUserId(testUser.getId())).thenReturn(userAreas);

        mockMvc.perform(get("/api/areas/user/{userId}", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("User Area 1"))
                .andExpect(jsonPath("$[1].name").value("User Area 2"));

        verify(areaRepository).findByUserId(testUser.getId());
    }

    @Test
    void testCreateArea() throws Exception {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);

        mockMvc.perform(post("/api/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(createAreaRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(testArea.getName()))
                .andExpect(jsonPath("$.description").value(testArea.getDescription()))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.userId").value(testUser.getId().toString()));

        verify(userRepository).findById(testUser.getId());
        verify(areaRepository).save(any(Area.class));
    }

    @Test
    void testCreateAreaWithNonExistentUser() throws Exception {
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(createAreaRequest)))
                .andExpect(status().isBadRequest());

        verify(userRepository).findById(testUser.getId());
        verify(areaRepository, never()).save(any(Area.class));
    }

    @Test
    void testCreateAreaWithInvalidData() throws Exception {
        CreateAreaRequest invalidRequest = new CreateAreaRequest();
        invalidRequest.setName(""); // Blank name should be invalid
        invalidRequest.setUserId(testUser.getId());

        mockMvc.perform(post("/api/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(areaRepository, never()).save(any(Area.class));
    }

    @Test
    void testUpdateArea() throws Exception {
        when(areaRepository.findById(testArea.getId())).thenReturn(Optional.of(testArea));
        
        Area updatedArea = new Area();
        updatedArea.setId(testArea.getId());
        updatedArea.setUser(testUser);
        updatedArea.setName("Updated Area");
        updatedArea.setDescription("Updated Description");
        updatedArea.setEnabled(testArea.getEnabled());
        
        when(areaRepository.save(any(Area.class))).thenReturn(updatedArea);

        CreateAreaRequest updateRequest = new CreateAreaRequest();
        updateRequest.setName("Updated Area");
        updateRequest.setDescription("Updated Description");
        updateRequest.setUserId(testUser.getId());
        mockMvc.perform(put("/api/areas/{id}", testArea.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testArea.getId().toString()))
                .andExpect(jsonPath("$.name").value("Updated Area"))
                .andExpect(jsonPath("$.description").value("Updated Description"));

        verify(areaRepository).findById(testArea.getId());
        verify(areaRepository).save(any(Area.class));
    }

    @Test
    void testUpdateAreaNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(areaRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/areas/{id}", nonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(objectMapper.writeValueAsString(createAreaRequest)))
                .andExpect(status().isNotFound());

        verify(areaRepository).findById(nonExistentId);
        verify(areaRepository, never()).save(any(Area.class));
    }

    @Test
    void testDeleteArea() throws Exception {
        when(areaRepository.existsById(testArea.getId())).thenReturn(true);

        mockMvc.perform(delete("/api/areas/{id}", testArea.getId())
                .with(csrf()))
                .andExpect(status().isNoContent());

        verify(areaRepository).existsById(testArea.getId());
        verify(areaRepository).deleteById(testArea.getId());
    }

    @Test
    void testDeleteAreaNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(areaRepository.existsById(nonExistentId)).thenReturn(false);

        mockMvc.perform(delete("/api/areas/{id}", nonExistentId)
                .with(csrf()))
                .andExpect(status().isNotFound());

        verify(areaRepository).existsById(nonExistentId);
        verify(areaRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void testToggleArea() throws Exception {
        boolean originalEnabledState = testArea.getEnabled();
        when(areaRepository.findById(testArea.getId())).thenReturn(Optional.of(testArea));
        
        Area toggledArea = new Area();
        toggledArea.setId(testArea.getId());
        toggledArea.setUser(testUser);
        toggledArea.setName(testArea.getName());
        toggledArea.setDescription(testArea.getDescription());
        toggledArea.setEnabled(!originalEnabledState); // Toggle enabled state
        
        when(areaRepository.save(any(Area.class))).thenReturn(toggledArea);

        mockMvc.perform(patch("/api/areas/{id}/toggle", testArea.getId())
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testArea.getId().toString()))
                .andExpect(jsonPath("$.enabled").value(!originalEnabledState));

        verify(areaRepository).findById(testArea.getId());
        verify(areaRepository).save(any(Area.class));
    }

    @Test
    void testToggleAreaNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(areaRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/areas/{id}/toggle", nonExistentId)
                .with(csrf()))
                .andExpect(status().isNotFound());

        verify(areaRepository).findById(nonExistentId);
        verify(areaRepository, never()).save(any(Area.class));
    }

    @Test
    void testSearchAreas() throws Exception {
        Area searchArea1 = new Area();
        searchArea1.setId(UUID.randomUUID());
        searchArea1.setUser(testUser);
        searchArea1.setName("Email Automation");
        searchArea1.setEnabled(true);

        Area searchArea2 = new Area();
        searchArea2.setId(UUID.randomUUID());
        searchArea2.setUser(testUser);
        searchArea2.setName("Email Notification");
        searchArea2.setEnabled(true);

        List<Area> searchResults = Arrays.asList(searchArea1, searchArea2);
        when(areaRepository.findByNameContainingIgnoreCase("email")).thenReturn(searchResults);

        mockMvc.perform(get("/api/areas/search")
                .param("name", "email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Email Automation"))
                .andExpect(jsonPath("$[1].name").value("Email Notification"));

        verify(areaRepository).findByNameContainingIgnoreCase("email");
    }

    @Test
    void testGetAllAreasWithDescendingSort() throws Exception {
        Page<Area> areaPage = new PageImpl<>(Arrays.asList(testArea));
        when(areaRepository.findAll(any(PageRequest.class))).thenReturn(areaPage);

        mockMvc.perform(get("/api/areas")
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "createdAt")
                .param("sortDir", "desc"))
                .andExpect(status().isOk());

        verify(areaRepository).findAll(PageRequest.of(0, 10, Sort.by("createdAt").descending()));
    }

    @Test
    void testGetAllAreasWithDefaultParameters() throws Exception {
        Page<Area> areaPage = new PageImpl<>(Arrays.asList(testArea));
        when(areaRepository.findAll(any(PageRequest.class))).thenReturn(areaPage);

        mockMvc.perform(get("/api/areas"))
                .andExpect(status().isOk());

        verify(areaRepository).findAll(PageRequest.of(0, 20, Sort.by("id").ascending()));
    }
}