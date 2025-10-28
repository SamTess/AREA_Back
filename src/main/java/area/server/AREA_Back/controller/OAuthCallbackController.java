package area.server.AREA_Back.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class OAuthCallbackController {

	@GetMapping("/oauth-callback")
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
					String decoded = new String(Base64.getDecoder().decode(state), StandardCharsets.UTF_8);

					ObjectMapper objectMapper = new ObjectMapper();
					JsonNode stateJson = objectMapper.readTree(decoded);

					if (stateJson.has("mobile_redirect")) {
						mobileRedirect = stateJson.get("mobile_redirect").asText();
					}

					if (stateJson.has("mode")) {
						mode = stateJson.get("mode").asText();
					}

					if (stateJson.has("provider")) {
						provider = stateJson.get("provider").asText();
					}
				} catch (Exception e) {
					log.error("Failed to decode state", e);
				}
			}

			if (mobileRedirect != null && !mobileRedirect.isEmpty()) {
				StringBuilder redirectUrl = new StringBuilder(mobileRedirect);

				if (error != null) {
					redirectUrl.append("?success=false");
					redirectUrl.append("&error=").append(URLEncoder.encode(error, StandardCharsets.UTF_8));
					if (errorDescription != null) {
						redirectUrl.append("&error_description=")
							.append(URLEncoder.encode(errorDescription, StandardCharsets.UTF_8));
					}
				} else if (code != null) {
					redirectUrl.append("?success=true");
					redirectUrl.append("&code=").append(URLEncoder.encode(code, StandardCharsets.UTF_8));
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
				frontendUrl = "http://localhost:3000";
			}

			StringBuilder webRedirectUrl = new StringBuilder(frontendUrl);
			webRedirectUrl.append("/oauth-callback");

			boolean hasParams = false;
			if (code != null) {
				webRedirectUrl.append("?code=").append(URLEncoder.encode(code, StandardCharsets.UTF_8));
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
