package area.server.AREA_Back.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
@Tag(name = "Test", description = "Contrôleur de test pour vérifier le bon fonctionnement de l'API")
public class TestController {

    @GetMapping("/health")
    @Operation(summary = "Test de santé", description = "Endpoint simple pour tester que l'API fonctionne")
    @ApiResponse(responseCode = "200", description = "API fonctionnelle")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "message", "L'API AREA fonctionne correctement",
            "timestamp", java.time.Instant.now().toString()
        ));
    }

    @GetMapping("/swagger-test")
    @Operation(summary = "Test Swagger", description = "Endpoint pour vérifier que Swagger UI fonctionne")
    @ApiResponse(responseCode = "200", description = "Swagger UI opérationnel")
    public ResponseEntity<Map<String, String>> swaggerTest() {
        return ResponseEntity.ok(Map.of(
            "swagger", "OK",
            "openapi", "WORKING",
            "documentation", "AVAILABLE"
        ));
    }
}