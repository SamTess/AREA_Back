package area.server.AREA_Back.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import area.server.AREA_Back.service.OAuthService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/api/oauth")
public class OAuthController {

    private final List<OAuthService> oauthServices;

    public OAuthController(List<OAuthService> oauthServices) {
        this.oauthServices = oauthServices;
    }

        @PostMapping("/{provider}/exchange")
        public ResponseEntity<String> exchangeToken(@PathVariable("provider") String provider,
                                                    @RequestParam("code") String code) {

        for (OAuthService service : oauthServices) {
            System.out.println("Registered OAuth Service: " + service.getProviderKey());
        }

        Optional<OAuthService> svc = oauthServices.stream()
            .filter(s -> s.getProviderKey().equalsIgnoreCase(provider))
            .findFirst();

        if (svc.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Provider not supported: " + provider);
        }

        try {
            String token = svc.get().exchangeToken(code);
            return ResponseEntity.ok(token);
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error exchanging token: " + e.getMessage());
        }
    }
}
