package area.server.AREA_Back.controller;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.ActionLinkResponse;
import area.server.AREA_Back.dto.BatchCreateActionLinksRequest;
import area.server.AREA_Back.dto.CreateActionLinkRequest;
import area.server.AREA_Back.entity.Area;
import area.server.AREA_Back.repository.AreaRepository;
import area.server.AREA_Back.service.Area.ActionLinkService;
import area.server.AREA_Back.service.Auth.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/areas/{areaId}/links")
@Slf4j
@Tag(name = "Action Links", description = "Manage links between actions/reactions")
@SecurityRequirement(name = "bearerAuth")
public class ActionLinkController {

    @Autowired
    private ActionLinkService actionLinkService;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private JwtService jwtService;

    @PostMapping
    @Operation(summary = "Create a link between two actions",
               description = "Creates a new link between a source action and a target action")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Link created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "404", description = "Area or action not found"),
        @ApiResponse(responseCode = "409", description = "Link already exists")
    })
    public ResponseEntity<ActionLinkResponse> createActionLink(
            @Parameter(description = "Area ID") @PathVariable UUID areaId,
            @Valid @RequestBody CreateActionLinkRequest request,
            HttpServletRequest httpRequest) {

        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!canUserAccessArea(userId, areaId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            log.info("Creating action link in area {}: {} -> {}",
                    areaId, request.getSourceActionInstanceId(), request.getTargetActionInstanceId());

            ActionLinkResponse response = actionLinkService.createActionLink(request, areaId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error creating action link: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/batch")
    @Operation(summary = "Create multiple links in batch",
               description = "Creates multiple links between actions in a single operation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Links created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "404", description = "Area or action not found")
    })
    public ResponseEntity<List<ActionLinkResponse>> createActionLinksBatch(
            @Parameter(description = "Area ID") @PathVariable UUID areaId,
            @Valid @RequestBody BatchCreateActionLinksRequest request,
            HttpServletRequest httpRequest) {

        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!canUserAccessArea(userId, areaId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            log.info("Creating {} action links in batch for area {}", request.getLinks().size(), areaId);

            request.setAreaId(areaId);

            List<ActionLinkResponse> responses = actionLinkService.createActionLinksBatch(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(responses);

        } catch (Exception e) {
            log.error("Error creating action links batch: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    @Operation(summary = "Get all links of an area",
               description = "Returns the list of all links between actions for a given area")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of links retrieved"),
        @ApiResponse(responseCode = "404", description = "Area not found")
    })
    public ResponseEntity<List<ActionLinkResponse>> getActionLinksByArea(
            @Parameter(description = "Area ID") @PathVariable UUID areaId,
            HttpServletRequest httpRequest) {

        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!canUserAccessArea(userId, areaId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            log.info("Getting action links for area {}", areaId);

            List<ActionLinkResponse> links = actionLinkService.getActionLinksByArea(areaId);
            return ResponseEntity.ok(links);

        } catch (Exception e) {
            log.error("Error getting action links: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{sourceActionId}/{targetActionId}")
    @Operation(summary = "Delete a link between two actions",
               description = "Deletes an existing link between a source action and a target action")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Link deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Link or action not found")
    })
    public ResponseEntity<Void> deleteActionLink(
            @Parameter(description = "Area ID") @PathVariable UUID areaId,
            @Parameter(description = "Source action ID") @PathVariable UUID sourceActionId,
            @Parameter(description = "Target action ID") @PathVariable UUID targetActionId,
            HttpServletRequest httpRequest) {

        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!canUserAccessArea(userId, areaId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            log.info("Deleting action link in area {}: {} -> {}", areaId, sourceActionId, targetActionId);

            actionLinkService.deleteActionLink(sourceActionId, targetActionId);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Error deleting action link: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping
    @Operation(summary = "Delete all links of an area",
               description = "Deletes all links between actions for a given area")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Links deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Area not found")
    })
    public ResponseEntity<Void> deleteAllActionLinksByArea(
            @Parameter(description = "Area ID") @PathVariable UUID areaId,
            HttpServletRequest httpRequest) {

        try {
            UUID userId = getUserIdFromRequest(httpRequest);

            if (!canUserAccessArea(userId, areaId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            log.info("Deleting all action links for area {}", areaId);

            actionLinkService.deleteActionLinksByArea(areaId);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Error deleting all action links: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean canUserAccessArea(UUID userId, UUID areaId) {
        try {
            Optional<Area> areaOpt = areaRepository.findById(areaId);
            if (areaOpt.isEmpty()) {
                return false;
            }
            Area area = areaOpt.get();
            return area.getUser().getId().equals(userId);
        } catch (Exception e) {
            log.error("Error checking user access to area: {}", e.getMessage());
            return false;
        }
    }
}
