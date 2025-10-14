package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.*;
import area.server.AREA_Back.entity.*;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.repository.*;
import area.server.AREA_Back.service.ServiceCacheService;
import area.server.AREA_Back.service.WorkerTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin-only endpoints for managing services, actions, reactions, and system metrics")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final ServiceRepository serviceRepository;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final AreaRepository areaRepository;
    private final UserRepository userRepository;
    private final ExecutionRepository executionRepository;
    private final ActionInstanceRepository actionInstanceRepository;
    private final ServiceCacheService serviceCacheService;
    private final WorkerTrackingService workerTrackingService;

    @PostMapping("/services")
    @Operation(summary = "Create a new service", description = "Admin endpoint to create a new service integration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Service created successfully"),
        @ApiResponse(responseCode = "409", description = "Service with this key already exists"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<?> createService(@Valid @RequestBody CreateServiceAdminRequest request) {
        try {
            if (serviceRepository.existsByKey(request.getKey())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Service with key '" + request.getKey() + "' already exists"));
            }

            Service service = new Service();
            service.setKey(request.getKey());
            service.setName(request.getName());
            service.setAuth(request.getAuth());
            service.setDocsUrl(request.getDocsUrl());
            service.setIconLightUrl(request.getIconLightUrl());
            service.setIconDarkUrl(request.getIconDarkUrl());
            service.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

            Service savedService = serviceRepository.save(service);
            serviceCacheService.invalidateServicesCache();

            log.info("Admin created service: {}", savedService.getKey());
            return ResponseEntity.status(HttpStatus.CREATED).body(convertToAdminServiceResponse(savedService));
        } catch (Exception e) {
            log.error("Error creating service", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create service", "message", e.getMessage()));
        }
    }

    @PutMapping("/services/{id}")
    @Operation(summary = "Update a service", description = "Admin endpoint to update an existing service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service updated successfully"),
        @ApiResponse(responseCode = "404", description = "Service not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<?> updateService(
            @PathVariable UUID id,
            @Valid @RequestBody CreateServiceAdminRequest request) {
        try {
            Optional<Service> optionalService = serviceRepository.findById(id);
            if (!optionalService.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Service not found"));
            }

            Service service = optionalService.get();

            if (!service.getKey().equals(request.getKey()) && serviceRepository.existsByKey(request.getKey())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Service with key '" + request.getKey() + "' already exists"));
            }

            service.setKey(request.getKey());
            service.setName(request.getName());
            service.setAuth(request.getAuth());
            service.setDocsUrl(request.getDocsUrl());
            service.setIconLightUrl(request.getIconLightUrl());
            service.setIconDarkUrl(request.getIconDarkUrl());
            if (request.getIsActive() != null) {
                service.setIsActive(request.getIsActive());
            }

            Service updatedService = serviceRepository.save(service);
            serviceCacheService.invalidateServicesCache();

            log.info("Admin updated service: {}", updatedService.getKey());
            return ResponseEntity.ok(convertToAdminServiceResponse(updatedService));
        } catch (Exception e) {
            log.error("Error updating service", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update service", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/services/{id}")
    @Operation(summary = "Delete a service", description = "Admin endpoint to delete a service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Service deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Service not found"),
        @ApiResponse(responseCode = "409", description = "Service has associated action definitions"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<?> deleteService(@PathVariable UUID id) {
        try {
            Optional<Service> optionalService = serviceRepository.findById(id);
            if (!optionalService.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Service not found"));
            }

            Service service = optionalService.get();

            List<ActionDefinition> actionDefinitions = actionDefinitionRepository.findByServiceId(id);
            if (!actionDefinitions.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                        "error", "Cannot delete service with existing action definitions",
                        "actionDefinitionsCount", actionDefinitions.size()
                    ));
            }

            serviceRepository.deleteById(id);
            serviceCacheService.invalidateServicesCache();

            log.info("Admin deleted service: {}", service.getKey());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting service", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete service", "message", e.getMessage()));
        }
    }

    @GetMapping("/services")
    @Operation(summary = "Get all services with usage stats", description = "Admin endpoint to list all services with usage statistics")
    public ResponseEntity<Page<AdminServiceResponse>> getAllServicesWithStats(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Service> services = serviceRepository.findAll(pageable);

        Page<AdminServiceResponse> responses = services.map(service -> {
            AdminServiceResponse response = convertToAdminServiceResponse(service);
            Long usageCount = actionInstanceRepository.countByActionDefinition_Service_Id(service.getId());
            response.setUsageCount(usageCount);
            return response;
        });

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get system metrics",
               description = "Returns system metrics including queue length, worker status, and execution statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<AdminMetricsResponse> getSystemMetrics() {
        try {
            Long queueLength = executionRepository.countByStatus(ExecutionStatus.QUEUED);

            LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
            Long failedExecutions = executionRepository.countByStatusAndCreatedAtAfter(ExecutionStatus.FAILED, yesterday);

            Long successfulExecutions = executionRepository.countByStatusAndCreatedAtAfter(ExecutionStatus.OK, yesterday);

            Long totalAreas = areaRepository.count();

            Long activeAreas = areaRepository.countByEnabled(true);

            Long totalUsers = userRepository.count();

            Map<String, Object> systemMetrics = new HashMap<>();
            systemMetrics.put("timestamp", LocalDateTime.now());
            systemMetrics.put("uptime", "N/A");
            systemMetrics.put("totalExecutions", executionRepository.count());
            systemMetrics.put("totalActionDefinitions", actionDefinitionRepository.count());
            systemMetrics.put("totalServices", serviceRepository.count());
            systemMetrics.put("workerStatistics", workerTrackingService.getWorkerStatistics());

            AdminMetricsResponse response = AdminMetricsResponse.builder()
                .queueLength(queueLength)
                .activeWorkers(workerTrackingService.getActiveWorkers())
                .totalWorkers(workerTrackingService.getTotalWorkers())
                .failedExecutions(failedExecutions)
                .successfulExecutions(successfulExecutions)
                .totalAreas(totalAreas)
                .activeAreas(activeAreas)
                .totalUsers(totalUsers)
                .systemMetrics(systemMetrics)
                .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching system metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/workers/statistics")
    @Operation(summary = "Get detailed worker statistics",
               description = "Returns detailed statistics about worker thread pools including active, total, and queue status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Worker statistics retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<Map<String, Object>> getWorkerStatistics() {
        try {
            return ResponseEntity.ok(workerTrackingService.getWorkerStatistics());
        } catch (Exception e) {
            log.error("Error fetching worker statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/workers/health")
    @Operation(summary = "Get worker health status",
               description = "Returns health status of worker thread pools")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Worker health status retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<Map<String, Object>> getWorkerHealth() {
        try {
            return ResponseEntity.ok(workerTrackingService.getHealthStatus());
        } catch (Exception e) {
            log.error("Error fetching worker health", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/areas/{id}/execute")
    @Operation(summary = "Force-run an AREA manually",
               description = "Manually triggers execution of an AREA for testing/debugging purposes")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "AREA execution triggered successfully"),
        @ApiResponse(responseCode = "404", description = "AREA not found"),
        @ApiResponse(responseCode = "400", description = "AREA has no action instances"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<?> forceExecuteArea(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> inputPayload) {
        try {
            Optional<Area> optionalArea = areaRepository.findById(id);
            if (!optionalArea.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "AREA not found"));
            }

            Area area = optionalArea.get();

            List<ActionInstance> actionInstances = actionInstanceRepository.findByAreaId(id);
            if (actionInstances.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "AREA has no action instances configured"));
            }

            ActionInstance triggerAction = actionInstances.stream()
                .filter(ai -> ai.getActionDefinition().getIsEventCapable())
                .findFirst()
                .orElse(actionInstances.get(0));

            Execution execution = new Execution();
            execution.setArea(area);
            execution.setActionInstance(triggerAction);
            execution.setStatus(ExecutionStatus.QUEUED);
            execution.setInputPayload(inputPayload != null ? inputPayload : new HashMap<>());

            Execution savedExecution = executionRepository.save(execution);

            log.info("Admin manually triggered AREA execution: {} for area: {}", savedExecution.getId(), area.getName());

            return ResponseEntity.ok(Map.of(
                "message", "AREA execution triggered successfully",
                "executionId", savedExecution.getId(),
                "areaId", area.getId(),
                "areaName", area.getName(),
                "status", savedExecution.getStatus().name().toLowerCase()
            ));
        } catch (Exception e) {
            log.error("Error triggering AREA execution", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to trigger AREA execution", "message", e.getMessage()));
        }
    }

    @GetMapping("/areas")
    @Operation(summary = "Get all areas with user info", description = "Admin endpoint to list all areas across all users")
    public ResponseEntity<Page<AdminAreaResponse>> getAllAreas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Area> areas = areaRepository.findAll(pageable);

        Page<AdminAreaResponse> responses = areas.map(this::convertToAdminAreaResponse);

        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/areas/{id}")
    @Operation(summary = "Update area status", description = "Admin endpoint to enable/disable an area")
    public ResponseEntity<?> updateAreaStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body) {
        try {
            Optional<Area> optionalArea = areaRepository.findById(id);
            if (!optionalArea.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "AREA not found"));
            }

            Area area = optionalArea.get();
            Boolean enabled = body.get("enabled");

            if (enabled != null) {
                area.setEnabled(enabled);
                areaRepository.save(area);
                log.info("Admin {} AREA: {}", enabled ? "enabled" : "disabled", area.getName());
            }

            return ResponseEntity.ok(convertToAdminAreaResponse(area));
        } catch (Exception e) {
            log.error("Error updating area status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update area status", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/areas/{id}")
    @Operation(summary = "Delete an area", description = "Admin endpoint to delete any area")
    public ResponseEntity<?> deleteArea(@PathVariable UUID id) {
        try {
            if (!areaRepository.existsById(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "AREA not found"));
            }

            areaRepository.deleteById(id);
            log.info("Admin deleted AREA: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting area", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete area", "message", e.getMessage()));
        }
    }

    @GetMapping("/services-usage")
    @Operation(summary = "Get services usage statistics")
    public ResponseEntity<List<Map<String, Object>>> getServicesUsage() {
        try {
            List<Service> services = serviceRepository.findAll();
            List<Map<String, Object>> usageStats = services.stream().map(service -> {
                Long usageCount = actionInstanceRepository.countByActionDefinition_Service_Id(service.getId());
                Map<String, Object> stat = new HashMap<>();
                stat.put("service", service.getName());
                stat.put("usage", usageCount);
                return stat;
            }).sorted((a, b) -> Long.compare((Long) b.get("usage"), (Long) a.get("usage")))
            .collect(Collectors.toList());

            return ResponseEntity.ok(usageStats);
        } catch (Exception e) {
            log.error("Error fetching services usage", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/logs")
    @Operation(summary = "Get system logs", description = "Get recent execution logs")
    public ResponseEntity<Page<Map<String, Object>>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("queuedAt").descending());
            Page<Execution> executions = executionRepository.findAll(pageable);

            Page<Map<String, Object>> logs = executions.map(execution -> {
                Map<String, Object> log = new HashMap<>();
                log.put("id", execution.getId());
                log.put("timestamp", execution.getQueuedAt());
                log.put("level", execution.getStatus() == ExecutionStatus.FAILED ? "ERROR" : "INFO");
                log.put("message", "Execution " + execution.getStatus().name() + " for area: " +
                       (execution.getArea() != null ? execution.getArea().getName() : "N/A"));
                log.put("source", "area-runner");
                return log;
            });

            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("Error fetching logs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/area-runs")
    @Operation(summary = "Get recent area runs")
    public ResponseEntity<Page<Map<String, Object>>> getAreaRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("queuedAt").descending());
            Page<Execution> executions = executionRepository.findAll(pageable);

            Page<Map<String, Object>> runs = executions.map(execution -> {
                Map<String, Object> run = new HashMap<>();
                run.put("id", execution.getId());
                run.put("areaName", execution.getArea() != null ? execution.getArea().getName() : "N/A");
                run.put("user", execution.getArea() != null ? execution.getArea().getUser().getEmail() : "N/A");
                run.put("timestamp", execution.getQueuedAt());
                run.put("status", execution.getStatus().name().toLowerCase());
                run.put("duration", execution.getFinishedAt() != null && execution.getQueuedAt() != null
                    ? java.time.Duration.between(execution.getQueuedAt(), execution.getFinishedAt()).getSeconds() + "s"
                    : "N/A");
                return run;
            });

            return ResponseEntity.ok(runs);
        } catch (Exception e) {
            log.error("Error fetching area runs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/area-stats")
    @Operation(summary = "Get area statistics")
    public ResponseEntity<List<Map<String, Object>>> getAreaStats() {
        try {
            Long total = areaRepository.count();
            LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
            Long successful = executionRepository.countByStatusAndCreatedAtAfter(ExecutionStatus.OK, yesterday);
            Long failed = executionRepository.countByStatusAndCreatedAtAfter(ExecutionStatus.FAILED, yesterday);
            Long inProgress = executionRepository.countByStatus(ExecutionStatus.QUEUED) +
                              executionRepository.countByStatus(ExecutionStatus.RUNNING);

            List<Map<String, Object>> stats = new ArrayList<>();
            stats.add(Map.of("title", "Total Areas", "value", total.toString(), "icon", "IconMap"));
            stats.add(Map.of("title", "Successful (24h)", "value", successful.toString(), "icon", "IconCheck"));
            stats.add(Map.of("title", "Failed (24h)", "value", failed.toString(), "icon", "IconX"));
            stats.add(Map.of("title", "In Progress", "value", inProgress.toString(), "icon", "IconClock"));

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching area stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/user-connected-per-day")
    @Operation(summary = "Get users connected per day")
    public ResponseEntity<List<Map<String, Object>>> getUsersConnectedPerDay(
            @RequestParam(defaultValue = "7") int days) {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);

            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

            List<Map<String, Object>> data = new ArrayList<>();
            List<Object[]> results = userRepository.findUsersConnectedPerDay(start, endExclusive);

            for (Object[] result : results) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("date", result[0].toString());
                entry.put("users", result[1]);
                data.add(entry);
            }

            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error fetching users connected per day", e);
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    @GetMapping("/new-user-per-month")
    @Operation(summary = "Get new users per month")
    public ResponseEntity<List<Map<String, Object>>> getNewUsersPerMonth(
            @RequestParam(defaultValue = "12") int months) {
        try {
            List<Map<String, Object>> data = new ArrayList<>();
            LocalDateTime endDate = LocalDateTime.now();

            for (int i = months - 1; i >= 0; i--) {
                LocalDateTime monthStart = endDate.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                LocalDateTime monthEnd = monthStart.plusMonths(1).minusSeconds(1);

                Long count = userRepository.countByCreatedAtBetween(monthStart, monthEnd);

                Map<String, Object> entry = new HashMap<>();
                entry.put("month", monthStart.getMonth().toString().substring(0, 3));
                entry.put("users", count);
                data.add(entry);
            }

            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error fetching new users per month", e);
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    @GetMapping("/users")
    @Operation(summary = "Get all users")
    public ResponseEntity<Page<Map<String, Object>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<User> users = userRepository.findAll(pageable);

            Page<Map<String, Object>> userResponses = users.map(user -> {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("name", user.getEmail().split("@")[0]);
                userMap.put("email", user.getEmail());
                userMap.put("role", user.getIsAdmin() ? "Admin" : "User");
                return userMap;
            });

            return ResponseEntity.ok(userResponses);
        } catch (Exception e) {
            log.error("Error fetching users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/card-user-data")
    @Operation(summary = "Get user statistics for dashboard cards")
    public ResponseEntity<List<Map<String, Object>>> getCardUserData() {
        try {
            Long totalUsers = userRepository.count();
            Long activeUsers = userRepository.countByIsActive(true);
            Long adminUsers = userRepository.countByIsAdmin(true);

            Long newUsersThisWeek = userRepository.countByCreatedAtAfter(LocalDateTime.now().minusWeeks(1));

            List<Map<String, Object>> data = new ArrayList<>();
            data.add(Map.of("title", "Total Users", "icon", "user", "value", totalUsers.toString(), "diff", newUsersThisWeek));
            data.add(Map.of("title", "Active Users", "icon", "user-check", "value", activeUsers.toString(), "diff", 0));
            data.add(Map.of("title", "Admins", "icon", "shield", "value", adminUsers.toString(), "diff", 0));

            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error fetching card user data", e);
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    private AdminServiceResponse convertToAdminServiceResponse(Service service) {
        AdminServiceResponse response = new AdminServiceResponse();
        response.setId(service.getId());
        response.setKey(service.getKey());
        response.setName(service.getName());
        response.setAuth(service.getAuth());
        response.setLogo(service.getIconLightUrl());
        response.setIsActive(service.getIsActive());
        response.setDocsUrl(service.getDocsUrl());
        response.setIconLightUrl(service.getIconLightUrl());
        response.setIconDarkUrl(service.getIconDarkUrl());
        response.setCreatedAt(service.getCreatedAt());
        response.setUpdatedAt(service.getUpdatedAt());
        return response;
    }

    private AdminAreaResponse convertToAdminAreaResponse(Area area) {
        AdminAreaResponse response = new AdminAreaResponse();
        response.setId(area.getId());
        response.setName(area.getName());
        response.setDescription(area.getDescription());
        response.setEnabled(area.getEnabled());
        response.setUser(area.getUser().getEmail().split("@")[0]);
        response.setUserEmail(area.getUser().getEmail());
        response.setUserId(area.getUser().getId());
        response.setCreatedAt(area.getCreatedAt());
        response.setUpdatedAt(area.getUpdatedAt());

        List<Execution> executions = executionRepository.findByAreaIdOrderByCreatedAtDesc(area.getId());
        if (!executions.isEmpty()) {
            Execution lastExecution = executions.get(0);
            response.setLastRun(lastExecution.getQueuedAt());
            response.setStatus(lastExecution.getStatus().name().toLowerCase());
        } else {
            response.setStatus("not started");
        }

        return response;
    }
}
