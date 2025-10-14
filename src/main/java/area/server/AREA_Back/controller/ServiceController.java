package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.CreateServiceRequest;
import area.server.AREA_Back.dto.ServiceResponse;
import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.service.ServiceCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/services")
@Tag(name = "Services", description = "API for managing services")
public class ServiceController {

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ServiceCacheService serviceCacheService;

    @GetMapping
    @Operation(summary = "Get all services",
               description = "Retrieves a paginated list of all services")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of services retrieved successfully")
    })
    public ResponseEntity<Page<ServiceResponse>> getAllServices(
            @Parameter(description = "Page number (starts at 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction (asc or desc)")
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Service> services = serviceRepository.findAll(pageable);
        Page<ServiceResponse> serviceResponses = services.map(this::convertToResponse);

        return ResponseEntity.ok(serviceResponses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponse> getServiceById(@PathVariable UUID id) {
        Optional<Service> service = serviceRepository.findById(id);
        if (service.isPresent()) {
            return ResponseEntity.ok(convertToResponse(service.get()));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/enabled")
    public ResponseEntity<List<ServiceResponse>> getEnabledServices() {
        List<Service> services = serviceRepository.findAllEnabledServices();
        List<ServiceResponse> serviceResponses = services.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(serviceResponses);
    }

    @GetMapping("/catalog")
    @Operation(summary = "Get services catalog",
               description = "Retrieves all services from cache with fallback to database. "
                           + "Optimized for public catalog display.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Services catalog retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<ServiceResponse>> getServicesCatalog() {
        try {
            List<ServiceResponse> services = serviceCacheService.getAllServicesCached();
            return ResponseEntity.ok(services);
        } catch (Exception e) {
            List<ServiceResponse> services = serviceCacheService.getAllServicesUncached();
            return ResponseEntity.ok(services);
        }
    }

    @GetMapping("/catalog/enabled")
    @Operation(summary = "Get enabled services catalog",
               description = "Retrieves only enabled services from cache with fallback to database")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Enabled services catalog retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<ServiceResponse>> getEnabledServicesCatalog() {
        try {
            List<ServiceResponse> services = serviceCacheService.getEnabledServicesCached();
            return ResponseEntity.ok(services);
        } catch (Exception e) {
            List<Service> services = serviceRepository.findAllEnabledServices();
            List<ServiceResponse> serviceResponses = services.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
            return ResponseEntity.ok(serviceResponses);
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new service", description = "Admin-only endpoint to create a new service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Service created successfully"),
        @ApiResponse(responseCode = "409", description = "Service with this key already exists"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<ServiceResponse> createService(@Valid @RequestBody CreateServiceRequest request) {
        if (serviceRepository.existsByKey(request.getKey())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Service service = new Service();
        service.setKey(request.getKey());
        service.setName(request.getName());
        service.setAuth(request.getAuth());
        service.setDocsUrl(request.getDocsUrl());
        service.setIconLightUrl(request.getIconLightUrl());
        service.setIconDarkUrl(request.getIconDarkUrl());
        service.setIsActive(true);

        Service savedService = serviceRepository.save(service);

        serviceCacheService.invalidateServicesCache();

        return ResponseEntity.status(HttpStatus.CREATED).body(convertToResponse(savedService));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a service", description = "Admin-only endpoint to update an existing service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service updated successfully"),
        @ApiResponse(responseCode = "404", description = "Service not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<ServiceResponse> updateService(
            @PathVariable UUID id,
            @Valid @RequestBody CreateServiceRequest request) {

        Optional<Service> optionalService = serviceRepository.findById(id);
        if (!optionalService.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Service service = optionalService.get();
        service.setKey(request.getKey());
        service.setName(request.getName());
        service.setAuth(request.getAuth());
        service.setDocsUrl(request.getDocsUrl());
        service.setIconLightUrl(request.getIconLightUrl());
        service.setIconDarkUrl(request.getIconDarkUrl());

        Service updatedService = serviceRepository.save(service);

        serviceCacheService.invalidateServicesCache();

        return ResponseEntity.ok(convertToResponse(updatedService));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a service", description = "Admin-only endpoint to delete a service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Service deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Service not found"),
        @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    })
    public ResponseEntity<Void> deleteService(@PathVariable UUID id) {
        if (!serviceRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        serviceRepository.deleteById(id);

        serviceCacheService.invalidateServicesCache();

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<ServiceResponse>> searchServices(@RequestParam String name) {
        List<Service> services = serviceRepository.findByNameContainingIgnoreCase(name);
        List<ServiceResponse> serviceResponses = services.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(serviceResponses);
    }

    private ServiceResponse convertToResponse(Service service) {
        ServiceResponse response = new ServiceResponse();
        response.setId(service.getId());
        response.setKey(service.getKey());
        response.setName(service.getName());
        response.setAuth(service.getAuth());
        response.setDocsUrl(service.getDocsUrl());
        response.setIconLightUrl(service.getIconLightUrl());
        response.setIconDarkUrl(service.getIconDarkUrl());
        response.setIsActive(service.getIsActive());
        response.setCreatedAt(service.getCreatedAt());
        response.setUpdatedAt(service.getUpdatedAt());
        return response;
    }
}