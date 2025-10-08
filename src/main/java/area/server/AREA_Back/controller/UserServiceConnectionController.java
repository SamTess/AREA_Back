package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.ServiceConnectionStatus;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.service.AuthService;
import area.server.AREA_Back.service.UserIdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
            status.setUserEmail(currentUser.getEmail());
            status.setAvatarUrl(currentUser.getAvatarUrl());

            if (oauthIdentity.isPresent()) {
                status.setConnected(true);
                status.setConnectionType("OAUTH");
                status.setProviderUserId(oauthIdentity.get().getProviderUserId());

                String userName = extractUserNameFromOAuth(oauthIdentity.get());
                status.setUserName(userName != null ? userName : currentUser.getEmail());
            } else {
                status.setConnected(false);
                status.setConnectionType("NONE");
                status.setUserName(currentUser.getEmail());
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

            List<ServiceConnectionStatus> connectedServices = oauthIdentities.stream()
                    .map(oauth -> {
                        ServiceConnectionStatus status = new ServiceConnectionStatus();
                        String serviceKey = mapOAuthProviderToServiceKey(oauth.getProvider());

                        status.setServiceKey(serviceKey);
                        status.setServiceName(getServiceDisplayName(serviceKey));
                        status.setIconUrl(getServiceIconUrl(serviceKey));
                        status.setConnected(true);
                        status.setConnectionType("OAUTH");
                        status.setUserEmail(currentUser.getEmail());
                        status.setAvatarUrl(currentUser.getAvatarUrl());
                        status.setProviderUserId(oauth.getProviderUserId());

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
}