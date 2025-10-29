package area.server.AREA_Back.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.OAuthProvider;
import area.server.AREA_Back.service.Auth.OAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/api/oauth")
@Tag(name = "OAuth", description = "API for managing oauth and providers")
@Slf4j
public class OAuthController {

    private final List<OAuthService> oauthServices;

    public OAuthController(List<OAuthService> oauthServices) {
        this.oauthServices = oauthServices;
    }

    @GetMapping("/providers")
    public ResponseEntity<List<OAuthProvider>> getProviders() {
        List<OAuthProvider> providerDtos = oauthServices.stream()
            .map(OAuthProvider::fromService)
            .toList();
        return ResponseEntity.ok(providerDtos);
    }

    @GetMapping("/{provider}/authorize")
    public ResponseEntity<String> authorize(@PathVariable("provider") String provider,
                                          HttpServletResponse response) {

        Optional<OAuthService> svc = oauthServices.stream()
            .filter(s -> s.getProviderKey().equalsIgnoreCase(provider))
            .findFirst();

        if (svc.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Provider not found");
        }

        try {
            String authorizationUrl = svc.get().getUserAuthUrl();
            response.setHeader("Location", authorizationUrl);
            response.setStatus(HttpServletResponse.SC_FOUND);
            return ResponseEntity.status(HttpStatus.FOUND).body("Redirecting to " + provider + "...");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to redirect to " + provider + ": " + e.getMessage());
        }
    }

    @PostMapping("/{provider}/exchange")
    public ResponseEntity<?> exchangeToken(@PathVariable("provider") String provider,
                                           @RequestBody Map<String, String> requestBody,
                                           HttpServletResponse response) {

        String authorizationCode = requestBody.get("code");
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Optional<OAuthService> svc = oauthServices.stream()
            .filter(s -> s.getProviderKey().equalsIgnoreCase(provider))
            .findFirst();

        if (svc.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            OAuthLoginRequest request = new OAuthLoginRequest(authorizationCode);
            AuthResponse result = svc.get().authenticate(request, response);
            return ResponseEntity.ok(result);
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (Exception e) {
            log.error("OAuth authentication failed for provider {}: {}", provider, e.getMessage(), e);

            if (e.getMessage() != null && (e.getMessage().contains("already linked")
                    || e.getMessage().contains("duplicate")
                    || e.getMessage().contains("unique constraint"))) {
                Map<String, String> error = Map.of(
                    "error", "already_linked",
                    "message", "This " + provider + " account is already linked to your user account"
                );
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }

            Map<String, String> error = Map.of(
                "error", "oauth_failed",
                "message", e.getMessage() != null ? e.getMessage() : "OAuth authentication failed"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}