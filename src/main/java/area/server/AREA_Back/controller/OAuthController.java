package area.server.AREA_Back.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.OAuthProvider;
import area.server.AREA_Back.service.Auth.OAuthService;
import area.server.AREA_Back.service.PKCEStore;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/oauth")
@Tag(name = "OAuth", description = "API for managing oauth and providers")
public class OAuthController {

    private final List<OAuthService> oauthServices;
    private final PKCEStore pkceStore;
    
    @Value("${OAUTH_REDIRECT_FRONTEND_URL:http://localhost:3000}")
    private String webRedirectBaseUrl;

    public OAuthController(List<OAuthService> oauthServices, PKCEStore pkceStore) {
        this.oauthServices = oauthServices;
        this.pkceStore = pkceStore;
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
                                          @RequestParam(value = "origin", required = false, defaultValue = "web") String origin,
                                          @RequestParam(value = "code_challenge", required = false) String codeChallenge,
                                          @RequestParam(value = "code_challenge_method", required = false, defaultValue = "S256") String codeChallengeMethod,
                                          HttpServletResponse response) {

        Optional<OAuthService> svc = oauthServices.stream()
            .filter(s -> s.getProviderKey().equalsIgnoreCase(provider))
            .findFirst();

        if (svc.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Provider not found");
        }

        try {
            String stateData = "origin=" + origin + "&provider=" + provider;
            String state = Base64.getUrlEncoder().encodeToString(stateData.getBytes(StandardCharsets.UTF_8));

            if (codeChallenge != null && !codeChallenge.isEmpty()) {
                pkceStore.store(state, codeChallenge, codeChallengeMethod);
            }
            
            String authorizationUrl = svc.get().getUserAuthUrl(state);
            response.setHeader("Location", authorizationUrl);
            response.setStatus(HttpServletResponse.SC_FOUND);
            return ResponseEntity.status(HttpStatus.FOUND).body("Redirecting to " + provider + "...");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to redirect to " + provider + ": " + e.getMessage());
        }
    }

    /**
     * Main OAuth callback handler (can be called from /api/oauth/callback or forwarded from /oauth-callback)
     */
    @GetMapping("/callback")
    public void oauthCallback(
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "state", required = false) String state,
        @RequestParam(value = "error", required = false) String error,
        @RequestParam(value = "error_description", required = false) String errorDescription,
        HttpServletResponse response) throws Exception {

        String origin = "web";
        String mode = "login";
        String provider = null;
        
        if (state != null && !state.isEmpty()) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);

                for (String param : decoded.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        if ("origin".equals(kv[0])) {
                            origin = kv[1];
                        } else if ("mode".equals(kv[0])) {
                            mode = kv[1];
                        } else if ("provider".equals(kv[0])) {
                            provider = kv[1];
                        }
                    }
                }
            } catch (Exception e) {
                log.error("❌ Failed to decode state", e);
            }
        }

        if (error != null) {
            if ("mobile".equals(origin)) {
                String redirectUrl = "areamobile://oauth/callback?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8);
                if (errorDescription != null) {
                    redirectUrl += "&error_description=" + URLEncoder.encode(errorDescription, StandardCharsets.UTF_8);
                }
                response.sendRedirect(redirectUrl);
            } else {
                String redirectUrl = webRedirectBaseUrl + "/oauth-callback?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8);
                if (errorDescription != null) {
                    redirectUrl += "&error_description=" + URLEncoder.encode(errorDescription, StandardCharsets.UTF_8);
                }
                response.sendRedirect(redirectUrl);
            }
            return;
        }

        if ("mobile".equals(origin)) {
            String redirectUrl = "areamobile://oauth/callback?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
            if (provider != null) {
                redirectUrl += "&provider=" + URLEncoder.encode(provider, StandardCharsets.UTF_8);
            }
            if (mode != null) {
                redirectUrl += "&mode=" + URLEncoder.encode(mode, StandardCharsets.UTF_8);
            }
            response.sendRedirect(redirectUrl);
        } else {
            String redirectUrl = webRedirectBaseUrl + "/oauth-callback?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
            if (state != null) {
                redirectUrl += "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
            }
            response.sendRedirect(redirectUrl);
        }
    }

    @PostMapping("/{provider}/exchange")
    public ResponseEntity<AuthResponse> exchangeToken(@PathVariable("provider") String provider,
                                                        @RequestBody Map<String, String> requestBody,
                                                        HttpServletResponse response,
                                                        HttpServletRequest request) {

        String authorizationCode = requestBody.get("code");
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String codeVerifier = requestBody.get("code_verifier");

        if (codeVerifier != null && !codeVerifier.isEmpty()) {
            System.out.println("PKCE code_verifier received for provider: " + provider);
        }

        Optional<OAuthService> svc = oauthServices.stream()
            .filter(s -> s.getProviderKey().equalsIgnoreCase(provider))
            .findFirst();

        if (svc.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            OAuthLoginRequest oauthRequest = new OAuthLoginRequest(authorizationCode);
            AuthResponse result = svc.get().authenticate(oauthRequest, response);

            return ResponseEntity.ok(result);
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}