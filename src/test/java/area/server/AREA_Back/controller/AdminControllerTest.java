package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.AdminMetricsResponse;
import area.server.AREA_Back.dto.CreateServiceAdminRequest;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.repository.*;
import area.server.AREA_Back.service.ServiceCacheService;
import area.server.AREA_Back.service.WorkerTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminController
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private ActionInstanceRepository actionInstanceRepository;

    @Mock
    private ServiceCacheService serviceCacheService;

    @Mock
    private WorkerTrackingService workerTrackingService;

    @InjectMocks
    private AdminController adminController;

    private Service testService;
    private Area testArea;
    private User testUser;
    private Execution testExecution;

    @BeforeEach
    void setUp() {
        // Setup test service
        testService = new Service();
        testService.setId(UUID.randomUUID());
        testService.setKey("test-service");
        testService.setName("Test Service");
        testService.setAuth(Service.AuthType.OAUTH2);
        testService.setIsActive(true);

        // Setup test user
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@example.com");
        testUser.setIsAdmin(true);

        // Setup test area
        testArea = new Area();
        testArea.setId(UUID.randomUUID());
        testArea.setName("Test Area");
        testArea.setDescription("Test Description");
        testArea.setEnabled(true);
        testArea.setUser(testUser);
        testArea.setCreatedAt(LocalDateTime.now());
        testArea.setUpdatedAt(LocalDateTime.now());

        // Setup test execution
        testExecution = new Execution();
        testExecution.setId(UUID.randomUUID());
        testExecution.setArea(testArea);
        testExecution.setStatus(ExecutionStatus.OK);
        testExecution.setQueuedAt(LocalDateTime.now());
    }

    @Test
    void testCreateService_Success() {
        // Arrange
        CreateServiceAdminRequest request = new CreateServiceAdminRequest();
        request.setKey("github");
        request.setName("GitHub");
        request.setAuth(Service.AuthType.OAUTH2);
        request.setIsActive(true);

        when(serviceRepository.existsByKey("github")).thenReturn(false);
        when(serviceRepository.save(any(Service.class))).thenReturn(testService);

        // Act
        ResponseEntity<?> response = adminController.createService(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(serviceRepository, times(1)).save(any(Service.class));
        verify(serviceCacheService, times(1)).invalidateServicesCache();
    }

    @Test
    void testCreateService_Conflict() {
        // Arrange
        CreateServiceAdminRequest request = new CreateServiceAdminRequest();
        request.setKey("github");
        request.setName("GitHub");
        request.setAuth(Service.AuthType.OAUTH2);

        when(serviceRepository.existsByKey("github")).thenReturn(true);

        // Act
        ResponseEntity<?> response = adminController.createService(request);

        // Assert
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(serviceRepository, never()).save(any(Service.class));
    }

    @Test
    void testUpdateService_Success() {
        // Arrange
        UUID serviceId = testService.getId();
        CreateServiceAdminRequest request = new CreateServiceAdminRequest();
        request.setKey("github-updated");
        request.setName("GitHub Updated");
        request.setAuth(Service.AuthType.OAUTH2);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(testService));
        when(serviceRepository.existsByKey("github-updated")).thenReturn(false);
        when(serviceRepository.save(any(Service.class))).thenReturn(testService);

        // Act
        ResponseEntity<?> response = adminController.updateService(serviceId, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(serviceRepository, times(1)).save(any(Service.class));
        verify(serviceCacheService, times(1)).invalidateServicesCache();
    }

    @Test
    void testUpdateService_NotFound() {
        // Arrange
        UUID serviceId = UUID.randomUUID();
        CreateServiceAdminRequest request = new CreateServiceAdminRequest();
        request.setKey("github");
        request.setName("GitHub");
        request.setAuth(Service.AuthType.OAUTH2);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<?> response = adminController.updateService(serviceId, request);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(serviceRepository, never()).save(any(Service.class));
    }

    @Test
    void testDeleteService_Success() {
        // Arrange
        UUID serviceId = testService.getId();
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(testService));
        when(actionDefinitionRepository.findByServiceId(serviceId)).thenReturn(Collections.emptyList());

        // Act
        ResponseEntity<?> response = adminController.deleteService(serviceId);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(serviceRepository, times(1)).deleteById(serviceId);
        verify(serviceCacheService, times(1)).invalidateServicesCache();
    }

    @Test
    void testDeleteService_HasActionDefinitions() {
        // Arrange
        UUID serviceId = testService.getId();
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(testService));
        when(actionDefinitionRepository.findByServiceId(serviceId))
            .thenReturn(Collections.singletonList(new area.server.AREA_Back.entity.ActionDefinition()));

        // Act
        ResponseEntity<?> response = adminController.deleteService(serviceId);

        // Assert
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(serviceRepository, never()).deleteById(any());
    }

    @Test
    void testGetSystemMetrics_Success() {
        // Arrange
        when(executionRepository.countByStatus(ExecutionStatus.QUEUED)).thenReturn(5L);
        when(executionRepository.countByStatusAndCreatedAtAfter(eq(ExecutionStatus.FAILED), any(LocalDateTime.class)))
            .thenReturn(3L);
        when(executionRepository.countByStatusAndCreatedAtAfter(eq(ExecutionStatus.OK), any(LocalDateTime.class)))
            .thenReturn(42L);
        when(areaRepository.count()).thenReturn(15L);
        when(areaRepository.countByEnabled(true)).thenReturn(12L);
        when(userRepository.count()).thenReturn(40L);
        when(executionRepository.count()).thenReturn(100L);
        when(actionDefinitionRepository.count()).thenReturn(50L);
        when(serviceRepository.count()).thenReturn(10L);
        
        // Mock WorkerTrackingService
        when(workerTrackingService.getActiveWorkers()).thenReturn(3);
        when(workerTrackingService.getTotalWorkers()).thenReturn(6);
        when(workerTrackingService.getWorkerStatistics()).thenReturn(new HashMap<>());

        // Act
        ResponseEntity<AdminMetricsResponse> response = adminController.getSystemMetrics();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(5L, response.getBody().getQueueLength());
        assertEquals(3, response.getBody().getActiveWorkers());
        assertEquals(6, response.getBody().getTotalWorkers());
        assertEquals(3L, response.getBody().getFailedExecutions());
        assertEquals(42L, response.getBody().getSuccessfulExecutions());
        assertEquals(15L, response.getBody().getTotalAreas());
        assertEquals(12L, response.getBody().getActiveAreas());
        assertEquals(40L, response.getBody().getTotalUsers());
        
        // Verify WorkerTrackingService was called
        verify(workerTrackingService).getActiveWorkers();
        verify(workerTrackingService).getTotalWorkers();
        verify(workerTrackingService).getWorkerStatistics();
    }

    @Test
    void testGetAllServicesWithStats() {
        // Arrange
        Page<Service> servicePage = new PageImpl<>(Collections.singletonList(testService));
        
        when(serviceRepository.findAll(any(PageRequest.class))).thenReturn(servicePage);
        when(actionInstanceRepository.countByActionDefinition_Service_Id(any(UUID.class))).thenReturn(5L);

        // Act
        ResponseEntity<?> response = adminController.getAllServicesWithStats(0, 20, "name", "asc");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(serviceRepository, times(1)).findAll(any(PageRequest.class));
    }

    @Test
    void testGetAllAreas() {
        // Arrange
        Page<Area> areaPage = new PageImpl<>(Collections.singletonList(testArea));
        
        when(areaRepository.findAll(any(PageRequest.class))).thenReturn(areaPage);
        when(executionRepository.findByAreaIdOrderByCreatedAtDesc(any(UUID.class)))
            .thenReturn(Collections.emptyList());

        // Act
        ResponseEntity<?> response = adminController.getAllAreas(0, 20, "createdAt", "desc");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(areaRepository, times(1)).findAll(any(PageRequest.class));
    }

    @Test
    void testUpdateAreaStatus_Success() {
        // Arrange
        UUID areaId = testArea.getId();
        Map<String, Boolean> body = new HashMap<>();
        body.put("enabled", false);

        when(areaRepository.findById(areaId)).thenReturn(Optional.of(testArea));
        when(areaRepository.save(any(Area.class))).thenReturn(testArea);

        // Act
        ResponseEntity<?> response = adminController.updateAreaStatus(areaId, body);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(areaRepository, times(1)).save(testArea);
    }

    @Test
    void testDeleteArea_Success() {
        // Arrange
        UUID areaId = testArea.getId();
        when(areaRepository.existsById(areaId)).thenReturn(true);

        // Act
        ResponseEntity<?> response = adminController.deleteArea(areaId);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(areaRepository, times(1)).deleteById(areaId);
    }

    @Test
    void testDeleteArea_NotFound() {
        // Arrange
        UUID areaId = UUID.randomUUID();
        when(areaRepository.existsById(areaId)).thenReturn(false);

        // Act
        ResponseEntity<?> response = adminController.deleteArea(areaId);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(areaRepository, never()).deleteById(any());
    }

    @Test
    void testGetServicesUsage() {
        // Arrange
        when(serviceRepository.findAll()).thenReturn(Collections.singletonList(testService));
        when(actionInstanceRepository.countByActionDefinition_Service_Id(any(UUID.class))).thenReturn(25L);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getServicesUsage();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
    }

    @Test
    void testGetAreaStats() {
        // Arrange
        when(areaRepository.count()).thenReturn(15L);
        when(executionRepository.countByStatusAndCreatedAtAfter(eq(ExecutionStatus.OK), any(LocalDateTime.class)))
            .thenReturn(8L);
        when(executionRepository.countByStatusAndCreatedAtAfter(eq(ExecutionStatus.FAILED), any(LocalDateTime.class)))
            .thenReturn(4L);
        when(executionRepository.countByStatus(ExecutionStatus.QUEUED)).thenReturn(2L);
        when(executionRepository.countByStatus(ExecutionStatus.RUNNING)).thenReturn(1L);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getAreaStats();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4, response.getBody().size());
    }

    @Test
    void testGetUsers() {
        // Arrange
        Page<User> userPage = new PageImpl<>(Collections.singletonList(testUser));
        
        when(userRepository.findAll(any(PageRequest.class))).thenReturn(userPage);

        // Act
        ResponseEntity<?> response = adminController.getUsers(0, 20);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository, times(1)).findAll(any(PageRequest.class));
    }

    @Test
    void testGetCardUserData() {
        // Arrange
        when(userRepository.count()).thenReturn(40L);
        when(userRepository.countByIsActive(true)).thenReturn(35L);
        when(userRepository.countByIsAdmin(true)).thenReturn(5L);
        when(userRepository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(5L);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getCardUserData();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
    }
}
