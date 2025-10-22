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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

@Slf4j
@RestController
@RequestMapping("/api/oauth")
@Tag(name = "OAuth", description = "API for managing oauth and providers")
public class OAuthController {

    private final List<OAuthService> oauthServices;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    public ResponseEntity<String> authorize(
            @PathVariable("provider") String provider,
            @RequestParam(value = "app_redirect_uri", required = false) String appRedirectUri,
            @RequestParam(value = "returnUrl", required = false, defaultValue = "/") String returnUrl,
            HttpServletResponse response) {

        Optional<OAuthService> svc = oauthServices.stream()
            .filter(s -> s.getProviderKey().equalsIgnoreCase(provider))
            .findFirst();

        if (svc.isEmpty()) {
            log.warn("Authorize: provider '{}' not found", provider);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Provider not found");
        }

        try {
            String baseAuthorizeUrl = svc.get().getUserAuthUrl();
            String finalAuthorizeUrl = baseAuthorizeUrl;

            if (appRedirectUri != null && !appRedirectUri.isBlank()) {
                Map<String, String> stateMap = new HashMap<>();
                stateMap.put("app_redirect_uri", appRedirectUri);
                stateMap.put("returnUrl", (returnUrl == null || returnUrl.isBlank()) ? "/" : returnUrl);
                stateMap.put("provider", provider.toLowerCase());

                String json;
                try {
                    json = objectMapper.writeValueAsString(stateMap);
                } catch (JsonProcessingException e) {
                    log.error(
                        "Authorize: failed to serialize state for provider={}, error={}",
                        provider,
                        e.getMessage(),
                        e
                    );
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to build state");
                }

                String encoded = URLEncoder.encode(json, StandardCharsets.UTF_8);
                finalAuthorizeUrl = baseAuthorizeUrl
                    + (baseAuthorizeUrl.contains("?") ? "&" : "?")
                    + "state=" + encoded;

                log.debug(
                    "Authorize: adding state for mobile hints provider={}, app_redirect_uri={}, returnUrl={}",
                    provider, appRedirectUri, returnUrl
                );
            }

            response.setHeader("Location", finalAuthorizeUrl);
            response.setStatus(HttpServletResponse.SC_FOUND);
            return ResponseEntity.status(HttpStatus.FOUND).body("Redirecting to " + provider + "...");

            } catch (Exception e) {
                log.error(
                    "Authorize: failed to build redirect for provider={}, error={}",
                    provider,
                    e.getMessage(),
                    e
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to redirect to " + provider + ": " + e.getMessage());
            }
    }

    public ResponseEntity<String> authorize(String provider, HttpServletResponse response) {
        return authorize(provider, null, "/", response);
    }


    @PostMapping("/{provider}/exchange")
    public ResponseEntity<AuthResponse> exchangeToken(@PathVariable("provider") String provider,
                                                        @RequestBody Map<String, String> requestBody,
                                                        HttpServletResponse response) {

        String authorizationCode = requestBody.get("code");
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            log.warn("Exchange: missing authorization code for provider={}", provider);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new AuthResponse("Missing authorization code", null));
        }

        Optional<OAuthService> svc = oauthServices.stream()
            .filter(s -> s.getProviderKey().equalsIgnoreCase(provider))
            .findFirst();

        if (svc.isEmpty()) {
            log.warn("Exchange: provider '{}' not found", provider);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AuthResponse("Provider not found", null));
        }

        try {
            OAuthLoginRequest request = new OAuthLoginRequest(authorizationCode);
            AuthResponse result = svc.get().authenticate(request, response);
            return ResponseEntity.ok(result);

        } catch (UnsupportedOperationException e) {
            log.warn("Exchange: provider '{}' not implemented", provider, e);
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new AuthResponse("Provider not implemented", null));
        } catch (Exception e) {
            log.error("Exchange: OAuth failed for provider={}, error={}", provider, e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new AuthResponse("OAuth exchange failed: " + e.getMessage(), null));
        }
    }
}