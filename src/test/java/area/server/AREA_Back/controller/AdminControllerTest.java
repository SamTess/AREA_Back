package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.AdminMetricsResponse;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.repository.*;
import area.server.AREA_Back.service.WorkerTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.springframework.data.domain.Sort;
import java.util.Arrays;

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

    // ========== Service CRUD tests removed - Now handled by ServiceController ==========

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
        List<Service> services = Collections.singletonList(testService);

        when(serviceRepository.findAll(org.mockito.ArgumentMatchers.any(Sort.class))).thenReturn(services);
            when(actionInstanceRepository.countByActionDefinitionServiceId(any(UUID.class))).thenReturn(5L);

        // Act
        ResponseEntity<?> response = adminController.getAllServicesWithStats();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(serviceRepository, times(1)).findAll(org.mockito.ArgumentMatchers.any(Sort.class));
    }

    @Test
    void testGetAllAreas() {
        // Arrange
        List<Area> areas = Collections.singletonList(testArea);

        when(areaRepository.findAll(org.mockito.ArgumentMatchers.any(Sort.class))).thenReturn(areas);
        when(executionRepository.findByAreaIdOrderByCreatedAtDesc(any(UUID.class)))
            .thenReturn(Collections.emptyList());

        // Act
        ResponseEntity<?> response = adminController.getAllAreas();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(areaRepository, times(1)).findAll(org.mockito.ArgumentMatchers.any(Sort.class));
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
    void testUpdateAreaStatus_NotFound() {
        // Arrange
        UUID areaId = UUID.randomUUID();
        Map<String, Boolean> body = new HashMap<>();
        body.put("enabled", false);

        when(areaRepository.findById(areaId)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<?> response = adminController.updateAreaStatus(areaId, body);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> errorBody = (Map<String, Object>) response.getBody();
        assertEquals("AREA not found", errorBody.get("error"));
        verify(areaRepository, never()).save(any(Area.class));
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
            when(actionInstanceRepository.countByActionDefinitionServiceId(any(UUID.class))).thenReturn(25L);

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
        List<User> users = Collections.singletonList(testUser);

        when(userRepository.findAll(org.mockito.ArgumentMatchers.any(Sort.class))).thenReturn(users);

        // Act
        ResponseEntity<?> response = adminController.getUsers();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository, times(1)).findAll(org.mockito.ArgumentMatchers.any(Sort.class));
    }

    @Test
    void testGetCardUserData() {
        // Arrange
        when(userRepository.count()).thenReturn(40L);
        when(userRepository.countByIsActive(true)).thenReturn(35L);
        when(userRepository.countByIsAdmin(true)).thenReturn(5L);
        when(userRepository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(5L);
        when(userRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(3L);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getCardUserData();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4, response.getBody().size());
    }

    @Test
    void testGetWorkerStatistics_Success() {
        // Arrange
        Map<String, Object> mockStats = new HashMap<>();
        mockStats.put("activeThreads", 5);
        mockStats.put("poolSize", 10);
        mockStats.put("queueSize", 3);
        
        when(workerTrackingService.getWorkerStatistics()).thenReturn(mockStats);

        // Act
        ResponseEntity<Map<String, Object>> response = adminController.getWorkerStatistics();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().get("activeThreads"));
        assertEquals(10, response.getBody().get("poolSize"));
        assertEquals(3, response.getBody().get("queueSize"));
        verify(workerTrackingService, times(1)).getWorkerStatistics();
    }

    @Test
    void testGetWorkerStatistics_Error() {
        // Arrange
        when(workerTrackingService.getWorkerStatistics()).thenThrow(new RuntimeException("Service error"));

        // Act
        ResponseEntity<Map<String, Object>> response = adminController.getWorkerStatistics();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        verify(workerTrackingService, times(1)).getWorkerStatistics();
    }

    @Test
    void testGetWorkerHealth_Success() {
        // Arrange
        Map<String, Object> mockHealth = new HashMap<>();
        mockHealth.put("status", "healthy");
        mockHealth.put("activeWorkers", 3);
        mockHealth.put("totalWorkers", 5);
        
        when(workerTrackingService.getHealthStatus()).thenReturn(mockHealth);

        // Act
        ResponseEntity<Map<String, Object>> response = adminController.getWorkerHealth();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("healthy", response.getBody().get("status"));
        assertEquals(3, response.getBody().get("activeWorkers"));
        assertEquals(5, response.getBody().get("totalWorkers"));
        verify(workerTrackingService, times(1)).getHealthStatus();
    }

    @Test
    void testGetWorkerHealth_Error() {
        // Arrange
        when(workerTrackingService.getHealthStatus()).thenThrow(new RuntimeException("Service error"));

        // Act
        ResponseEntity<Map<String, Object>> response = adminController.getWorkerHealth();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        verify(workerTrackingService, times(1)).getHealthStatus();
    }

    @Test
    void testGetLogs_Success() {
        // Arrange
        testExecution.setStatus(ExecutionStatus.FAILED);
        testExecution.setQueuedAt(LocalDateTime.now());
        
        org.springframework.data.domain.Page<Execution> mockPage = 
            new org.springframework.data.domain.PageImpl<>(Collections.singletonList(testExecution));
        
        when(executionRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(mockPage);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getLogs(50);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        
        Map<String, Object> log = response.getBody().get(0);
        assertEquals(testExecution.getId(), log.get("id"));
        assertEquals("ERROR", log.get("level"));
        assertEquals("area-runner", log.get("source"));
        assertTrue(log.get("message").toString().contains("FAILED"));
        
        verify(executionRepository, times(1)).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void testGetLogs_WithSuccessfulExecution() {
        // Arrange
        testExecution.setStatus(ExecutionStatus.OK);
        testExecution.setQueuedAt(LocalDateTime.now());
        
        org.springframework.data.domain.Page<Execution> mockPage = 
            new org.springframework.data.domain.PageImpl<>(Collections.singletonList(testExecution));
        
        when(executionRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(mockPage);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getLogs(10);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> log = response.getBody().get(0);
        assertEquals("INFO", log.get("level"));
    }

    @Test
    void testGetLogs_Error() {
        // Arrange
        when(executionRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getLogs(50);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testGetAreaRuns_Success() {
        // Arrange
        testExecution.setStatus(ExecutionStatus.OK);
        testExecution.setQueuedAt(LocalDateTime.now().minusMinutes(5));
        testExecution.setFinishedAt(LocalDateTime.now());
        
        org.springframework.data.domain.Page<Execution> mockPage = 
            new org.springframework.data.domain.PageImpl<>(Collections.singletonList(testExecution));
        
        when(executionRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(mockPage);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getAreaRuns(50);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        
        Map<String, Object> run = response.getBody().get(0);
        assertEquals(testExecution.getId(), run.get("id"));
        assertEquals("Test Area", run.get("areaName"));
        assertEquals("test@example.com", run.get("user"));
        assertEquals("ok", run.get("status"));
        assertNotNull(run.get("timestamp"));
        assertTrue(run.get("duration").toString().endsWith("s"));
        
        verify(executionRepository, times(1)).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void testGetAreaRuns_WithNullArea() {
        // Arrange
        testExecution.setArea(null);
        testExecution.setStatus(ExecutionStatus.RUNNING);
        testExecution.setQueuedAt(LocalDateTime.now());
        
        org.springframework.data.domain.Page<Execution> mockPage = 
            new org.springframework.data.domain.PageImpl<>(Collections.singletonList(testExecution));
        
        when(executionRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(mockPage);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getAreaRuns(10);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> run = response.getBody().get(0);
        assertEquals("N/A", run.get("areaName"));
        assertEquals("N/A", run.get("user"));
        assertEquals("running", run.get("status"));
    }

    @Test
    void testGetAreaRuns_WithNullFinishedAt() {
        // Arrange
        testExecution.setStatus(ExecutionStatus.QUEUED);
        testExecution.setQueuedAt(LocalDateTime.now());
        testExecution.setFinishedAt(null);
        
        org.springframework.data.domain.Page<Execution> mockPage = 
            new org.springframework.data.domain.PageImpl<>(Collections.singletonList(testExecution));
        
        when(executionRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(mockPage);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getAreaRuns(10);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> run = response.getBody().get(0);
        assertEquals("N/A", run.get("duration"));
    }

    @Test
    void testGetAreaRuns_Error() {
        // Arrange
        when(executionRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getAreaRuns(50);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testGetUsersConnectedPerDay_Success() {
        // Arrange
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{"2025-10-23", 10L});
        mockResults.add(new Object[]{"2025-10-22", 8L});
        
        when(userRepository.findUsersConnectedPerDay(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(mockResults);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getUsersConnectedPerDay(7);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        
        Map<String, Object> firstDay = response.getBody().get(0);
        assertEquals("2025-10-23", firstDay.get("date"));
        assertEquals(10L, firstDay.get("users"));
        
        verify(userRepository, times(1)).findUsersConnectedPerDay(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void testGetUsersConnectedPerDay_EmptyResults() {
        // Arrange
        when(userRepository.findUsersConnectedPerDay(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getUsersConnectedPerDay(7);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testGetUsersConnectedPerDay_Error() {
        // Arrange
        when(userRepository.findUsersConnectedPerDay(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getUsersConnectedPerDay(7);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testGetNewUsersPerMonth_Success() {
        // Arrange
        when(userRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(5L, 8L, 12L);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getNewUsersPerMonth(3);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
        
        Map<String, Object> firstMonth = response.getBody().get(0);
        assertNotNull(firstMonth.get("month"));
        assertEquals(5L, firstMonth.get("users"));
        
        verify(userRepository, times(3)).countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void testGetNewUsersPerMonth_SingleMonth() {
        // Arrange
        when(userRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(10L);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getNewUsersPerMonth(1);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void testGetNewUsersPerMonth_Error() {
        // Arrange
        when(userRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getNewUsersPerMonth(12);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testGetServicesUsage_Sorting() {
        // Arrange
        Service service1 = new Service();
        service1.setId(UUID.randomUUID());
        service1.setName("Service A");
        
        Service service2 = new Service();
        service2.setId(UUID.randomUUID());
        service2.setName("Service B");
        
        List<Service> services = Arrays.asList(service1, service2);
        
        when(serviceRepository.findAll()).thenReturn(services);
        when(actionInstanceRepository.countByActionDefinitionServiceId(service1.getId())).thenReturn(10L);
        when(actionInstanceRepository.countByActionDefinitionServiceId(service2.getId())).thenReturn(25L);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getServicesUsage();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        
        // Verify sorting - highest usage first
        Map<String, Object> first = response.getBody().get(0);
        Map<String, Object> second = response.getBody().get(1);
        
        assertEquals("Service B", first.get("service"));
        assertEquals(25L, first.get("usage"));
        
        assertEquals("Service A", second.get("service"));
        assertEquals(10L, second.get("usage"));
    }

    @Test
    void testGetServicesUsage_Error() {
        // Arrange
        when(serviceRepository.findAll()).thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getServicesUsage();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testDeleteArea_Error() {
        // Arrange
        UUID areaId = UUID.randomUUID();
        when(areaRepository.existsById(areaId)).thenReturn(true);
        doThrow(new RuntimeException("Delete failed")).when(areaRepository).deleteById(areaId);

        // Act
        ResponseEntity<?> response = adminController.deleteArea(areaId);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
    }

    @Test
    void testUpdateAreaStatus_WithNullEnabled() {
        // Arrange
        UUID areaId = testArea.getId();
        Map<String, Boolean> body = new HashMap<>();
        // Don't put "enabled" key, so it will be null

        when(areaRepository.findById(areaId)).thenReturn(Optional.of(testArea));

        // Act
        ResponseEntity<?> response = adminController.updateAreaStatus(areaId, body);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(areaRepository, never()).save(any(Area.class));
    }

    @Test
    void testUpdateAreaStatus_Error() {
        // Arrange
        UUID areaId = testArea.getId();
        Map<String, Boolean> body = new HashMap<>();
        body.put("enabled", false);

        when(areaRepository.findById(areaId)).thenReturn(Optional.of(testArea));
        when(areaRepository.save(any(Area.class))).thenThrow(new RuntimeException("Save failed"));

        // Act
        ResponseEntity<?> response = adminController.updateAreaStatus(areaId, body);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
    }

    @Test
    void testGetUsers_Error() {
        // Arrange
        when(userRepository.findAll(any(Sort.class))).thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<?> response = adminController.getUsers();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testGetAreaStats_Error() {
        // Arrange
        when(areaRepository.count()).thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getAreaStats();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testGetSystemMetrics_Error() {
        // Arrange
        when(executionRepository.countByStatus(ExecutionStatus.QUEUED))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<AdminMetricsResponse> response = adminController.getSystemMetrics();

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testGetCardUserData_Error() {
        // Arrange
        when(userRepository.count()).thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getCardUserData();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testGetCardUserData_WithZeroTotalUsersLastMonth() {
        // Arrange
        when(userRepository.count()).thenReturn(5L);
        when(userRepository.countByIsActive(true)).thenReturn(5L);
        when(userRepository.countByIsAdmin(true)).thenReturn(1L);
        when(userRepository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(5L);
        when(userRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getCardUserData();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4, response.getBody().size());
    }

    @Test
    void testCalculatePercentageDiff_WithZeroPrevious() {
        // This tests the private method indirectly through getCardUserData
        // Arrange
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByIsActive(true)).thenReturn(8L);
        when(userRepository.countByIsAdmin(true)).thenReturn(2L);
        when(userRepository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(5L);
        when(userRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getCardUserData();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // When previous is 0 and current > 0, diff should be 100
        Map<String, Object> newUsersCard = response.getBody().get(2);
        assertEquals(100, newUsersCard.get("diff"));
    }

    @Test
    void testCalculatePercentageDiff_WithNullPreviousAndZeroCurrent() {
        // This tests the edge case where both previous and current are 0
        // Arrange
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.countByIsActive(true)).thenReturn(0L);
        when(userRepository.countByIsAdmin(true)).thenReturn(0L);
        when(userRepository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(0L);
        when(userRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);

        // Act
        ResponseEntity<List<Map<String, Object>>> response = adminController.getCardUserData();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // When both are 0, diff should be 0
        Map<String, Object> newUsersCard = response.getBody().get(2);
        assertEquals(0, newUsersCard.get("diff"));
    }

    @Test
    void testConvertToAdminAreaResponse_WithNoExecutions() {
        // Arrange
        when(executionRepository.findByAreaIdOrderByCreatedAtDesc(testArea.getId()))
            .thenReturn(Collections.emptyList());

        when(areaRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(testArea));

        // Act
        ResponseEntity<?> response = adminController.getAllAreas();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(executionRepository, times(1)).findByAreaIdOrderByCreatedAtDesc(testArea.getId());
    }

    @Test
    void testConvertToAdminAreaResponse_WithExecutions() {
        // Arrange
        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setArea(testArea);
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setQueuedAt(LocalDateTime.now());
        
        when(executionRepository.findByAreaIdOrderByCreatedAtDesc(testArea.getId()))
            .thenReturn(Collections.singletonList(execution));

        when(areaRepository.findAll(any(Sort.class))).thenReturn(Collections.singletonList(testArea));

        // Act
        ResponseEntity<?> response = adminController.getAllAreas();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(executionRepository, times(1)).findByAreaIdOrderByCreatedAtDesc(testArea.getId());
    }
}
