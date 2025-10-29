package area.server.AREA_Back.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.OAuthProvider;
import area.server.AREA_Back.service.Auth.OAuthService;
import area.server.AREA_Back.service.Auth.OAuthStateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;


@RestController
@RequestMapping("/api/oauth")
@Tag(name = "OAuth", description = "API for managing oauth and providers")
@Slf4j
public class OAuthController {

    private final List<OAuthService> oauthServices;
    private final OAuthStateService oauthStateService;

    public OAuthController(List<OAuthService> oauthServices, OAuthStateService oauthStateService) {
        this.oauthServices = oauthServices;
        this.oauthStateService = oauthStateService;
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
                                          @org.springframework.web.bind.annotation.RequestParam(required = false) String mobile_redirect,
                                          @org.springframework.web.bind.annotation.RequestParam(required = false) String origin,
                                          @org.springframework.web.bind.annotation.RequestParam(required = false) String mode,
                                          HttpServletResponse response) {

        Optional<OAuthService> svc = oauthServices.stream()
            .filter(s -> s.getProviderKey().equalsIgnoreCase(provider))
            .findFirst();

        if (svc.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Provider not found");
        }

        try {
            String authorizationUrl = svc.get().getUserAuthUrl();
            String secureState = oauthStateService.createSecureState(
                mobile_redirect,
                origin != null ? origin : "web",
                mode != null ? mode : "login",
                provider.toLowerCase()
            );
            authorizationUrl += "&state=" + java.net.URLEncoder.encode(secureState, java.nio.charset.StandardCharsets.UTF_8);
            response.setHeader("Location", authorizationUrl);
            response.setStatus(HttpServletResponse.SC_FOUND);
            return ResponseEntity.status(HttpStatus.FOUND).body("Redirecting to " + provider + "...");

        } catch (Exception e) {
            log.error("Failed to create OAuth authorization URL for provider: {}", provider, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to redirect to " + provider + ": " + e.getMessage());
        }
    }

    @PostMapping("/{provider}/exchange")
    public ResponseEntity<AuthResponse> exchangeToken(@PathVariable("provider") String provider,
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}