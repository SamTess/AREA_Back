package area.server.AREA_Back.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import area.server.AREA_Back.service.Auth.OAuthStateService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OAuthCallbackController {

    private final OAuthStateService oauthStateService;

    @GetMapping("/api/oauth-callback")
    public void handleOAuthCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            HttpServletResponse response) {

        try {
            String mobileRedirect = null;
            String mode = "login";
            String provider = "google";

            if (state != null && !state.isEmpty()) {
                try {
                    Map<String, String> stateData = oauthStateService.validateAndParseState(state);
                    mobileRedirect = stateData.get("mobile_redirect");
                    mode = stateData.getOrDefault("mode", "login");
                    provider = stateData.getOrDefault("provider", "google");
                    log.info("OAuth state validated successfully for provider: {}", provider);
                } catch (SecurityException e) {
                    log.error("OAuth state validation failed: {}", e.getMessage());
                    response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Invalid OAuth state - possible security attack");
                    return;
                } catch (Exception e) {
                    log.error("Failed to parse OAuth state", e);
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid OAuth state format");
                    return;
                }
            }

            if (mobileRedirect != null && !mobileRedirect.isEmpty()) {
                StringBuilder redirectUrl = new StringBuilder(mobileRedirect);

                if (error != null) {
                    redirectUrl.append("?success=false");
                    redirectUrl.append("&error=")
                        .append(URLEncoder.encode(error, StandardCharsets.UTF_8));
                    if (errorDescription != null) {
                        redirectUrl.append("&error_description=")
                            .append(URLEncoder.encode(errorDescription, StandardCharsets.UTF_8));
                    }
                } else if (code != null) {
                    redirectUrl.append("?success=true");
                    redirectUrl.append("&code=")
                        .append(URLEncoder.encode(code, StandardCharsets.UTF_8));
                    redirectUrl.append("&mode=").append(mode);
                    redirectUrl.append("&provider=").append(provider);
                } else {
                    redirectUrl.append("?success=false&error=no_code");
                }

                response.sendRedirect(redirectUrl.toString());
                return;
            }

            String frontendUrl = System.getenv("FRONTEND_URL");
            if (frontendUrl == null || frontendUrl.isEmpty() || frontendUrl.contains("{{")) {
                String env = System.getenv("SPRING_PROFILES_ACTIVE");
                if ("prod".equals(env) || "production".equals(env)) {
                    log.warn("FRONTEND_URL not set in production, using default HTTPS URL");
                    frontendUrl = "https://localhost:3000";
                } else {
                    frontendUrl = "http://localhost:3000";
                }
            }

            StringBuilder webRedirectUrl = new StringBuilder(frontendUrl);
            webRedirectUrl.append("/oauth-callback");

            boolean hasParams = false;
            if (code != null) {
                webRedirectUrl.append("?code=")
                    .append(URLEncoder.encode(code, StandardCharsets.UTF_8));
                hasParams = true;
            }

            if (error != null) {
                webRedirectUrl.append(hasParams ? "&" : "?");
                webRedirectUrl.append("error=").append(URLEncoder.encode(error, StandardCharsets.UTF_8));
                hasParams = true;

                if (errorDescription != null) {
                    webRedirectUrl.append("&error_description=")
                        .append(URLEncoder.encode(errorDescription, StandardCharsets.UTF_8));
                }
            }

            if (state != null) {
                webRedirectUrl.append(hasParams ? "&" : "?");
                webRedirectUrl.append("state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
            }

            response.sendRedirect(webRedirectUrl.toString());

        } catch (Exception e) {
            log.error("Error handling OAuth callback", e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Failed to process OAuth callback");
            } catch (Exception ex) {
                log.error("Failed to send error response", ex);
            }
        }
    }
}
