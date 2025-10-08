package area.server.AREA_Back.controller;

import area.server.AREA_Back.dto.OAuthLinkErrorResponse;
import area.server.AREA_Back.dto.ServiceConnectionStatus;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.service.AuthService;
import area.server.AREA_Back.service.OAuthGithubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/oauth-link")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "OAuth Account Linking", description = "API for linking OAuth accounts to existing users")
public class OAuthLinkController {

    private final AuthService authService;
    private final OAuthGithubService oauthGithubService;

    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_CONFLICT = 409;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;

    @PostMapping("/{provider}/exchange")
    @Operation(summary = "Link OAuth account to current user without changing session")
    public ResponseEntity<?> linkOAuthAccount(
            @PathVariable String provider,
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {

        try {
            User currentUser = authService.getCurrentUserEntity(request);
            if (currentUser == null) {
                return ResponseEntity.status(HTTP_UNAUTHORIZED).build();
            }

            String authorizationCode = requestBody.get("code");
            if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            UserOAuthIdentity linkedIdentity;
            switch (provider.toLowerCase()) {
                case "github":
                    linkedIdentity = oauthGithubService.linkToExistingUser(currentUser, authorizationCode);
                    break;
                default:
                    return ResponseEntity.status(HTTP_NOT_FOUND).build();
            }

            ServiceConnectionStatus status = new ServiceConnectionStatus();
            status.setServiceKey(provider);
            status.setServiceName(getServiceDisplayName(provider));
            status.setIconUrl(getServiceIconUrl(provider));
            status.setConnected(true);
            status.setConnectionType("OAUTH");
            status.setUserEmail(currentUser.getEmail());
            status.setUserName(currentUser.getEmail());
            status.setAvatarUrl(currentUser.getAvatarUrl());
            status.setProviderUserId(linkedIdentity.getProviderUserId());

            return ResponseEntity.ok(status);

        } catch (RuntimeException e) {
            log.error("Error linking OAuth account for provider: {}", provider, e);

            String errorMessage = e.getMessage();
            if (errorMessage.contains("already linked to another user")) {
                return ResponseEntity.status(HTTP_CONFLICT)
                    .body(OAuthLinkErrorResponse.accountAlreadyLinked(getServiceDisplayName(provider)));
            } else if (errorMessage.contains("email is required")) {
                return ResponseEntity.status(HTTP_BAD_REQUEST)
                    .body(OAuthLinkErrorResponse.emailRequired(getServiceDisplayName(provider)));
            } else {
                return ResponseEntity.status(HTTP_INTERNAL_SERVER_ERROR)
                    .body(OAuthLinkErrorResponse.internalError());
            }
        } catch (Exception e) {
            log.error("Unexpected error linking OAuth account for provider: {}", provider, e);
            return ResponseEntity.status(HTTP_INTERNAL_SERVER_ERROR)
                .body(OAuthLinkErrorResponse.internalError());
        }
    }

    private String getServiceDisplayName(String provider) {
        return switch (provider.toLowerCase()) {
            case "github" -> "GitHub";
            case "google" -> "Google";
            case "microsoft" -> "Microsoft";
            default -> provider;
        };
    }

    private String getServiceIconUrl(String provider) {
        return switch (provider.toLowerCase()) {
            case "github" -> "https://upload.wikimedia.org/wikipedia/commons/9/91/Octicons-mark-github.svg";
            case "google" -> "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/Google_%22G%22_logo.svg/1024px-Google_%22G%22_logo.svg.png";
            case "microsoft" -> "https://upload.wikimedia.org/wikipedia/commons/4/44/Microsoft_logo.svg";
            default -> "/file.svg";
        };
    }
}