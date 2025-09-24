package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.AreaResponse;
import area.server.AREA_Back.dto.CreateAreaRequest;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/api/areas")
@Tag(name = "Areas", description = "API de gestion des areas (automatisations)")
public class AreaController {

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<AreaResponse>> getAllAreas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Area> areas = areaRepository.findAll(pageable);
        Page<AreaResponse> areaResponses = areas.map(this::convertToResponse);

        return ResponseEntity.ok(areaResponses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AreaResponse> getAreaById(@PathVariable UUID id) {
        Optional<Area> area = areaRepository.findById(id);
        if (area.isPresent()) {
            return ResponseEntity.ok(convertToResponse(area.get()));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AreaResponse>> getAreasByUserId(@PathVariable UUID userId) {
        List<Area> areas = areaRepository.findByUserId(userId);
        List<AreaResponse> areaResponses = areas.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(areaResponses);
    }

    @PostMapping
    public ResponseEntity<AreaResponse> createArea(@Valid @RequestBody CreateAreaRequest request) {
        Optional<User> user = userRepository.findById(request.getUserId());
        if (!user.isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        Area area = new Area();
        area.setName(request.getName());
        area.setDescription(request.getDescription());
        area.setUser(user.get());
        area.setEnabled(true);

        Area savedArea = areaRepository.save(area);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToResponse(savedArea));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AreaResponse> updateArea(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAreaRequest request) {

        Optional<Area> optionalArea = areaRepository.findById(id);
        if (!optionalArea.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Area area = optionalArea.get();
        area.setName(request.getName());
        area.setDescription(request.getDescription());

        Area updatedArea = areaRepository.save(area);
        return ResponseEntity.ok(convertToResponse(updatedArea));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArea(@PathVariable UUID id) {
        if (!areaRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        areaRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<AreaResponse> toggleArea(@PathVariable UUID id) {
        Optional<Area> optionalArea = areaRepository.findById(id);
        if (!optionalArea.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Area area = optionalArea.get();
        area.setEnabled(!area.getEnabled());
        Area updatedArea = areaRepository.save(area);

        return ResponseEntity.ok(convertToResponse(updatedArea));
    }

    @GetMapping("/search")
    public ResponseEntity<List<AreaResponse>> searchAreas(@RequestParam String name) {
        List<Area> areas = areaRepository.findByNameContainingIgnoreCase(name);
        List<AreaResponse> areaResponses = areas.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(areaResponses);
    }

    private AreaResponse convertToResponse(Area area) {
        AreaResponse response = new AreaResponse();
        response.setId(area.getId());
        response.setName(area.getName());
        response.setDescription(area.getDescription());
        response.setEnabled(area.getEnabled());
        response.setUserId(area.getUser().getId());
        response.setUserEmail(area.getUser().getEmail());
        response.setCreatedAt(area.getCreatedAt());
        response.setUpdatedAt(area.getUpdatedAt());
        return response;
    }
}