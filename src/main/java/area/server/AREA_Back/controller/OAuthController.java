package area.server.AREA_Back.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import area.server.AREA_Back.dto.AuthResponse;
import area.server.AREA_Back.dto.OAuthLoginRequest;
import area.server.AREA_Back.dto.OAuthProvider;
import area.server.AREA_Back.service.OAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/api/oauth")
@Tag(name = "OAuth", description = "API for managing oauth and providers")
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

    @PostMapping("/{provider}/exchange")
    public ResponseEntity<AuthResponse> exchangeToken(  @PathVariable("provider") String provider,
                                                        @RequestParam("code") String authorizationCode,
                                                        HttpServletResponse response) {

        Optional<OAuthService> svc = oauthServices.stream()
            .filter(s -> s.getProviderKey().equalsIgnoreCase(provider))
            .findFirst();

        if (svc.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

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
