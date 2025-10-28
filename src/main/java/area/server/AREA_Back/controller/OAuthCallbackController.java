package area.server.AREA_Back.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Legacy OAuth Callback Controller
 * Handles /oauth-callback endpoint (without /api/oauth prefix)
 * for OAuth providers configured with this redirect URL
 */
@RestController
public class OAuthCallbackController {

    private final OAuthController oauthController;

    public OAuthCallbackController(OAuthController oauthController) {
        this.oauthController = oauthController;
    }

    /**
     * Legacy callback endpoint at /oauth-callback
     * Forwards to OAuthController.oauthCallback()
     */
    @GetMapping("/oauth-callback")
    public void oauthCallback(
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "state", required = false) String state,
        @RequestParam(value = "error", required = false) String error,
        @RequestParam(value = "error_description", required = false) String errorDescription,
        HttpServletResponse response) throws Exception {
        
        // Forward to the main OAuth controller callback handler
        oauthController.oauthCallback(code, state, error, errorDescription, response);
    }
}
