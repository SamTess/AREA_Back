package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.ServiceConnectionStatus;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.service.Auth.AuthService;
import area.server.AREA_Back.service.Auth.UserIdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Service Connections", description = "API for managing user service connections")
public class UserServiceConnectionController {

    private final UserIdentityService userIdentityService;
    private final AuthService authService;

    private static final int HTTP_UNAUTHORIZED = 401;

    @GetMapping("/service-connection/{serviceKey}")
    @Operation(summary = "Get service connection status for current user")
    public ResponseEntity<ServiceConnectionStatus> getServiceConnectionStatus(
            @PathVariable String serviceKey,
            HttpServletRequest request) {

        try {
            User currentUser = authService.getCurrentUserEntity(request);
            if (currentUser == null) {
                return ResponseEntity.status(HTTP_UNAUTHORIZED).build();
            }

            String provider = mapServiceKeyToOAuthProvider(serviceKey);

            Optional<UserOAuthIdentity> oauthIdentity = userIdentityService.getOAuthIdentity(
                    currentUser.getId(), provider);

            ServiceConnectionStatus status = new ServiceConnectionStatus();
            status.setServiceKey(serviceKey);
            status.setServiceName(getServiceDisplayName(serviceKey));
            status.setIconUrl(getServiceIconUrl(serviceKey));
            status.setAvatarUrl(currentUser.getAvatarUrl());

            if (oauthIdentity.isPresent()) {
                UserOAuthIdentity oauth = oauthIdentity.get();
                status.setConnected(true);
                status.setConnectionType("OAUTH");
                status.setProviderUserId(oauth.getProviderUserId());

                String oauthEmail = extractEmailFromOAuth(oauth);
                status.setUserEmail(oauthEmail != null ? oauthEmail : currentUser.getEmail());

                boolean canDisconnect = userIdentityService.canDisconnectService(currentUser.getId(), provider);
                status.setCanDisconnect(canDisconnect);

                Optional<String> primaryProvider = userIdentityService.getPrimaryOAuthProvider(currentUser.getId());
                boolean isPrimary = primaryProvider.isPresent() && primaryProvider.get().equalsIgnoreCase(provider);
                status.setPrimaryAuth(isPrimary);

                String userName = extractUserNameFromOAuth(oauth);
                status.setUserName(userName != null ? userName : currentUser.getEmail());
            } else {
                status.setConnected(false);
                status.setConnectionType("NONE");
                status.setUserName(currentUser.getEmail());
                status.setUserEmail(currentUser.getEmail());
                status.setCanDisconnect(false);
                status.setPrimaryAuth(false);
            }

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Error getting service connection status for service: {}", serviceKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/connected-services")
    @Operation(summary = "Get all connected services for current user")
    public ResponseEntity<List<ServiceConnectionStatus>> getConnectedServices(HttpServletRequest request) {

        try {
            User currentUser = authService.getCurrentUserEntity(request);
            if (currentUser == null) {
                return ResponseEntity.status(HTTP_UNAUTHORIZED).build();
            }

            List<UserOAuthIdentity> oauthIdentities = userIdentityService.getUserOAuthIdentities(currentUser.getId());
            Optional<String> primaryProvider = userIdentityService.getPrimaryOAuthProvider(currentUser.getId());

            List<ServiceConnectionStatus> connectedServices = oauthIdentities.stream()
                    .map(oauth -> {
                        ServiceConnectionStatus status = new ServiceConnectionStatus();
                        String serviceKey = mapOAuthProviderToServiceKey(oauth.getProvider());

                        status.setServiceKey(serviceKey);
                        status.setServiceName(getServiceDisplayName(serviceKey));
                        status.setIconUrl(getServiceIconUrl(serviceKey));
                        status.setConnected(true);
                        status.setConnectionType("OAUTH");

                        String oauthEmail = extractEmailFromOAuth(oauth);
                        status.setUserEmail(oauthEmail != null ? oauthEmail : currentUser.getEmail());

                        status.setAvatarUrl(currentUser.getAvatarUrl());
                        status.setProviderUserId(oauth.getProviderUserId());

                        boolean canDisconnect = userIdentityService.canDisconnectService(currentUser.getId(), oauth.getProvider());
                        status.setCanDisconnect(canDisconnect);

                        log.info("Service: {}, Provider: {}, canDisconnect: {}", serviceKey, oauth.getProvider(), canDisconnect);

                        boolean isPrimary = primaryProvider.isPresent() && primaryProvider.get().equalsIgnoreCase(oauth.getProvider());
                        status.setPrimaryAuth(isPrimary);

                        String userName = extractUserNameFromOAuth(oauth);
                        status.setUserName(userName != null ? userName : currentUser.getEmail());

                        return status;
                    })
                    .toList();

            return ResponseEntity.ok(connectedServices);

        } catch (Exception e) {
            log.error("Error getting connected services", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String mapServiceKeyToOAuthProvider(String serviceKey) {
        return switch (serviceKey.toLowerCase()) {
            case "github" -> "github";
            case "google" -> "google";
            case "microsoft" -> "microsoft";
            default -> serviceKey.toLowerCase();
        };
    }

    private String mapOAuthProviderToServiceKey(String provider) {
        return switch (provider.toLowerCase()) {
            case "github" -> "github";
            case "google" -> "google";
            case "microsoft" -> "microsoft";
            default -> provider.toLowerCase();
        };
    }

    private String getServiceDisplayName(String serviceKey) {
        return switch (serviceKey.toLowerCase()) {
            case "github" -> "GitHub";
            case "google" -> "Google";
            case "microsoft" -> "Microsoft";
            default -> serviceKey;
        };
    }

    private String getServiceIconUrl(String serviceKey) {
        return switch (serviceKey.toLowerCase()) {
            case "github" -> "https://upload.wikimedia.org/wikipedia/commons/9/91/Octicons-mark-github.svg";
            case "google" -> "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/Google_%22G%22_logo.svg/1024px-Google_%22G%22_logo.svg.png";
            case "microsoft" -> "https://upload.wikimedia.org/wikipedia/commons/4/44/Microsoft_logo.svg";
            default -> "/file.svg";
        };
    }

    private String extractUserNameFromOAuth(UserOAuthIdentity oauth) {
        if (oauth.getTokenMeta() != null) {
            Object name = oauth.getTokenMeta().get("name");
            if (name != null) {
                return name.toString();
            }

            Object login = oauth.getTokenMeta().get("login");
            if (login != null) {
                return login.toString();
            }
        }
        return null;
    }

    private String extractEmailFromOAuth(UserOAuthIdentity oauth) {
        if (oauth.getTokenMeta() != null) {
            Object email = oauth.getTokenMeta().get("email");
            if (email != null) {
                return email.toString();
            }
        }
        return null;
    }

    @DeleteMapping("/service-connection/{serviceKey}")
    @Transactional
    @Operation(summary = "Disconnect service for current user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service disconnected successfully"),
        @ApiResponse(responseCode = "400", description = "Cannot disconnect primary authentication provider"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "404", description = "Service connection not found")
    })
    public ResponseEntity<Map<String, String>> disconnectService(
            @PathVariable String serviceKey,
            HttpServletRequest request) {
        try {
            User currentUser = authService.getCurrentUserEntity(request);
            if (currentUser == null) {
                return ResponseEntity.status(HTTP_UNAUTHORIZED).build();
            }

            String provider = mapServiceKeyToOAuthProvider(serviceKey);

            Optional<UserOAuthIdentity> oauthIdentity = userIdentityService.getOAuthIdentity(
                    currentUser.getId(), provider);

            if (oauthIdentity.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Service connection not found"));
            }

            if (!userIdentityService.canDisconnectService(currentUser.getId(), provider)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Cannot disconnect primary authentication provider"));
            }

            userIdentityService.disconnectService(currentUser.getId(), provider);

            log.info("Service {} disconnected for user {}", serviceKey, currentUser.getId());

            return ResponseEntity.ok(Map.of("message", "Service disconnected successfully"));

        } catch (IllegalStateException e) {
            log.warn("Failed to disconnect service {}: {}", serviceKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error disconnecting service: {}", serviceKey, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to disconnect service"));
        }
    }
}