package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.AreaDraftRequest;
import area.server.AREA_Back.dto.AreaDraftResponse;
import area.server.AREA_Back.dto.CreateAreaWithActionsAndLinksRequest;
import area.server.AREA_Back.dto.AreaResponse;
import area.server.AREA_Back.service.Area.AreaDraftCacheService;
import area.server.AREA_Back.service.Area.AreaService;
import area.server.AREA_Back.service.Auth.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/areas/drafts")
@Tag(name = "Area Drafts", description = "API for managing area drafts in cache")
@Slf4j
@RequiredArgsConstructor
public class AreaDraftController {

    private final AreaDraftCacheService draftCacheService;
    private final AreaService areaService;
    private final JwtService jwtService;

    @PostMapping
    @Operation(summary = "Save area draft to cache")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Draft saved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "503", description = "Cache service unavailable")
    })
    public ResponseEntity<Map<String, String>> saveDraft(
            @Valid @RequestBody AreaDraftRequest request,
            @RequestParam(required = false) String areaId,
            HttpServletRequest httpRequest) {
        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!draftCacheService.isCacheAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Cache service unavailable"));
            }

            UUID areaUuid = null;
            if (areaId != null && !areaId.isEmpty()) {
                try {
                    areaUuid = UUID.fromString(areaId);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid areaId format: {}", areaId);
                }
            }

            String draftId = draftCacheService.saveDraft(userId, request, areaUuid);

            return ResponseEntity.ok(Map.of(
                "draftId", draftId,
                "message", "Draft saved successfully"
            ));
        } catch (RuntimeException e) {
            log.error("Failed to save draft", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Failed to save draft", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error saving draft", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping
    @Operation(summary = "Get all user drafts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Drafts retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "503", description = "Cache service unavailable")
    })
    public ResponseEntity<?> getUserDrafts(HttpServletRequest httpRequest) {
        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!draftCacheService.isCacheAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Cache service unavailable"));
            }

            List<AreaDraftResponse> drafts = draftCacheService.getUserDrafts(userId);
            return ResponseEntity.ok(drafts);
        } catch (Exception e) {
            log.error("Failed to get drafts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve drafts"));
        }
    }

    @GetMapping("/edit/{areaId}")
    @Operation(summary = "Get edit draft for a specific area")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Edit draft retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Edit draft not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "503", description = "Cache service unavailable")
    })
    public ResponseEntity<?> getEditDraft(
            @PathVariable String areaId,
            HttpServletRequest httpRequest) {
        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!draftCacheService.isCacheAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Cache service unavailable"));
            }

            UUID areaUuid = UUID.fromString(areaId);
            Optional<AreaDraftResponse> draft = draftCacheService.getEditDraft(userId, areaUuid);

            if (draft.isPresent()) {
                return ResponseEntity.ok(draft.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Edit draft not found"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid area ID format"));
        } catch (Exception e) {
            log.error("Failed to get edit draft", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve edit draft"));
        }
    }

    @GetMapping("/{draftId}")
    @Operation(summary = "Get specific draft")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Draft retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Draft not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "503", description = "Cache service unavailable")
    })
    public ResponseEntity<?> getDraft(
            @PathVariable String draftId,
            HttpServletRequest httpRequest) {
        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!draftCacheService.isCacheAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Cache service unavailable"));
            }

            Optional<AreaDraftResponse> draft = draftCacheService.getDraft(userId, draftId);
            if (draft.isPresent()) {
                return ResponseEntity.ok(draft.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Draft not found"));
            }
        } catch (Exception e) {
            log.error("Failed to get draft", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retrieve draft"));
        }
    }

    @PostMapping("/{draftId}/commit")
    @Operation(summary = "Convert draft to permanent area")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Area created successfully from draft"),
        @ApiResponse(responseCode = "404", description = "Draft not found"),
        @ApiResponse(responseCode = "400", description = "Invalid draft data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "503", description = "Cache service unavailable")
    })
    public ResponseEntity<?> commitDraft(
            @PathVariable String draftId,
            HttpServletRequest httpRequest) {
        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!draftCacheService.isCacheAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Cache service unavailable"));
            }

            Optional<AreaDraftResponse> draftOpt = draftCacheService.getDraft(userId, draftId);
            if (draftOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Draft not found"));
            }

            AreaDraftResponse draft = draftOpt.get();

            CreateAreaWithActionsAndLinksRequest areaRequest = new CreateAreaWithActionsAndLinksRequest();
            areaRequest.setName(draft.getName());
            areaRequest.setDescription(draft.getDescription());
            areaRequest.setUserId(userId);
            areaRequest.setActions(draft.getActions());
            areaRequest.setReactions(draft.getReactions());
            areaRequest.setLayoutMode(draft.getLayoutMode());

            if (draft.getConnections() != null && !draft.getConnections().isEmpty()) {
                List<CreateAreaWithActionsAndLinksRequest.ActionConnectionRequest> connections =
                    draft.getConnections().stream()
                        .map(conn -> {
                            CreateAreaWithActionsAndLinksRequest.ActionConnectionRequest newConn =
                                new CreateAreaWithActionsAndLinksRequest.ActionConnectionRequest();
                            newConn.setSourceServiceId(conn.getSourceServiceId());
                            newConn.setTargetServiceId(conn.getTargetServiceId());
                            newConn.setLinkType(conn.getLinkType());
                            newConn.setMapping(conn.getMapping());
                            newConn.setCondition(conn.getCondition());
                            newConn.setOrder(conn.getOrder());
                            return newConn;
                        })
                        .collect(Collectors.toList());
                areaRequest.setConnections(connections);
            }

            AreaResponse areaResponse = areaService.createAreaWithActionsAndLinks(areaRequest);

            try {
                draftCacheService.deleteDraft(userId, draftId);
                log.info("Draft committed and area created: {}", areaResponse.getId());
            } catch (Exception e) {
                log.warn("Area created successfully but failed to delete draft {}: {}",
                    draftId, e.getMessage());
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(areaResponse);
        } catch (IllegalArgumentException e) {
            log.error("Invalid draft data: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid draft data", "message", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Failed to commit draft", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Failed to commit draft", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error committing draft", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }

    @DeleteMapping("/{draftId}")
    @Operation(summary = "Delete draft")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Draft deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "503", description = "Cache service unavailable")
    })
    public ResponseEntity<?> deleteDraft(
            @PathVariable String draftId,
            HttpServletRequest httpRequest) {
        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!draftCacheService.isCacheAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Cache service unavailable"));
            }

            draftCacheService.deleteDraft(userId, draftId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("Failed to delete draft", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Failed to delete draft", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error deleting draft", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }

    @PatchMapping("/{draftId}/extend")
    @Operation(summary = "Extend draft TTL")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Draft TTL extended successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "503", description = "Cache service unavailable")
    })
    public ResponseEntity<Map<String, String>> extendDraftTTL(
            @PathVariable String draftId,
            HttpServletRequest httpRequest) {
        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!draftCacheService.isCacheAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Cache service unavailable"));
            }

            draftCacheService.extendDraftTTL(userId, draftId);
            return ResponseEntity.ok(Map.of("message", "Draft TTL extended successfully"));
        } catch (Exception e) {
            log.error("Failed to extend draft TTL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to extend draft TTL"));
        }
    }

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        try {
            String accessToken = extractAccessTokenFromCookies(request);
            if (accessToken == null) {
                throw new RuntimeException("Access token not found in cookies");
            }
            return jwtService.extractUserIdFromAccessToken(accessToken);
        } catch (Exception e) {
            log.error("Failed to extract user ID from request: {}", e.getMessage());
            throw new RuntimeException("Failed to extract user ID", e);
        }
    }

    private String extractAccessTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("authToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
