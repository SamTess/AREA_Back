package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.AdminAreaResponse;
import area.server.AREA_Back.dto.AdminMetricsResponse;
import area.server.AREA_Back.dto.AdminServiceResponse;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.Execution;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.enums.ExecutionStatus;
import area.server.AREA_Back.repository.ActionDefinitionRepository;
import area.server.AREA_Back.repository.ActionInstanceRepository;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.ExecutionRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.WorkerTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final WorkerTrackingService workerTrackingService;

    @GetMapping("/services")
    @Operation(summary = "Get all services with usage stats",
               description = "Admin endpoint to list all services with usage statistics")
    public ResponseEntity<List<AdminServiceResponse>> getAllServicesWithStats() {
        List<Service> services = serviceRepository.findAll(Sort.by("name").ascending());

        List<AdminServiceResponse> responses = services.stream().map(service -> {
            AdminServiceResponse response = convertToAdminServiceResponse(service);
            Long usageCount = actionInstanceRepository.countByActionDefinitionServiceId(service.getId());
            response.setUsageCount(usageCount);
            return response;
        }).collect(Collectors.toList());

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

            final int hoursInDay = 24;
            LocalDateTime yesterday = LocalDateTime.now().minusHours(hoursInDay);
            Long failedExecutions =
                executionRepository.countByStatusAndCreatedAtAfter(ExecutionStatus.FAILED, yesterday);

            Long successfulExecutions =
                executionRepository.countByStatusAndCreatedAtAfter(ExecutionStatus.OK, yesterday);

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
               description = "Returns detailed statistics about worker thread pools "
                   + "including active, total, and queue status")
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

    @GetMapping("/areas")
    @Operation(summary = "Get all areas with user info",
               description = "Admin endpoint to list all areas across all users")
    public ResponseEntity<List<AdminAreaResponse>> getAllAreas() {
        List<Area> areas = areaRepository.findAll(Sort.by("createdAt").descending());

        List<AdminAreaResponse> responses = areas.stream()
            .map(this::convertToAdminAreaResponse)
            .collect(Collectors.toList());

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
                Long usageCount = actionInstanceRepository.countByActionDefinitionServiceId(service.getId());
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
    public ResponseEntity<List<Map<String, Object>>> getLogs(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit, Sort.by("queuedAt").descending());
            Page<Execution> executions = executionRepository.findAll(pageable);

            List<Map<String, Object>> logs = executions.stream().map(execution -> {
                Map<String, Object> log = new HashMap<>();
                log.put("id", execution.getId());
                log.put("timestamp", execution.getQueuedAt());
                log.put("level", execution.getStatus() == ExecutionStatus.FAILED ? "ERROR" : "INFO");
                String areaName = execution.getArea() != null ? execution.getArea().getName() : "N/A";
                log.put("message", "Execution " + execution.getStatus().name() + " for area: "
                       + areaName);
                log.put("source", "area-runner");
                return log;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("Error fetching logs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/area-runs")
    @Operation(summary = "Get recent area runs")
    public ResponseEntity<List<Map<String, Object>>> getAreaRuns(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit, Sort.by("queuedAt").descending());
            Page<Execution> executions = executionRepository.findAll(pageable);

            List<Map<String, Object>> runs = executions.stream().map(execution -> {
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
            }).collect(Collectors.toList());

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
            final int hoursInDay = 24;
            LocalDateTime yesterday = LocalDateTime.now().minusHours(hoursInDay);
            Long successful =
                executionRepository.countByStatusAndCreatedAtAfter(ExecutionStatus.OK, yesterday);
            Long failed =
                executionRepository.countByStatusAndCreatedAtAfter(ExecutionStatus.FAILED, yesterday);
            Long inProgress = executionRepository.countByStatus(ExecutionStatus.QUEUED)
                              + executionRepository.countByStatus(ExecutionStatus.RUNNING);

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
                LocalDateTime monthStart = endDate.minusMonths(i).withDayOfMonth(1)
                    .withHour(0).withMinute(0).withSecond(0);
                LocalDateTime monthEnd = monthStart.plusMonths(1).minusSeconds(1);

                Long count = userRepository.countByCreatedAtBetween(monthStart, monthEnd);

                Map<String, Object> entry = new HashMap<>();
                final int monthNameLength = 3;
                entry.put("month", monthStart.getMonth().toString().substring(0, monthNameLength));
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
    public ResponseEntity<List<Map<String, Object>>> getUsers() {
        try {
            List<User> users = userRepository.findAll(Sort.by("createdAt").descending());

            List<Map<String, Object>> userResponses = users.stream().map(user -> {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("id", user.getId());
                userMap.put("name", user.getEmail().split("@")[0]);
                userMap.put("email", user.getEmail());
                userMap.put("role", user.getIsAdmin() ? "Admin" : "User");
                return userMap;
            }).collect(Collectors.toList());

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

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneMonthAgo = now.minusMonths(1);
            LocalDateTime twoMonthsAgo = now.minusMonths(2);

            Long newUsersThisMonth = userRepository.countByCreatedAtAfter(oneMonthAgo);
            Long newUsersLastMonth = userRepository.countByCreatedAtBetween(twoMonthsAgo, oneMonthAgo);

            final double activeUsersRatio = 0.9;
            Long totalUsersLastMonth = totalUsers - newUsersThisMonth;
            Long activeUsersLastMonth;
            if (totalUsersLastMonth > 0) {
                activeUsersLastMonth = (long) (activeUsers * activeUsersRatio);
            } else {
                activeUsersLastMonth = activeUsers;
            }

            int totalUsersDiff = calculatePercentageDiff(totalUsers, totalUsersLastMonth);
            int activeUsersDiff = calculatePercentageDiff(activeUsers, activeUsersLastMonth);
            int newUsersDiff = calculatePercentageDiff(newUsersThisMonth, newUsersLastMonth);

            List<Map<String, Object>> data = new ArrayList<>();
            data.add(Map.of(
                "title", "Total Users",
                "icon", "user",
                "value", totalUsers.toString(),
                "diff", totalUsersDiff
            ));
            data.add(Map.of(
                "title", "Active Users",
                "icon", "user",
                "value", activeUsers.toString(),
                "diff", activeUsersDiff
            ));
            data.add(Map.of(
                "title", "New Users",
                "icon", "user",
                "value", newUsersThisMonth.toString(),
                "diff", newUsersDiff
            ));
            data.add(Map.of(
                "title", "Admins",
                "icon", "user",
                "value", adminUsers.toString(),
                "diff", 0
            ));

            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error fetching card user data", e);
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    private int calculatePercentageDiff(Long current, Long previous) {
        final int percent = 100;
        if (previous == null || previous == 0) {
            if (current > 0) {
                return percent;
            }
            return 0;
        }
        double diff = ((current - previous) * (double) percent) / previous;
        return (int) Math.round(diff);
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
