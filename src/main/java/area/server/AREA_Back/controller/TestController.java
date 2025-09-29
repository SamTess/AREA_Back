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
@Tag(name = "Test", description = "Test controller to verify the API is working")
public class TestController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Simple endpoint to verify the API is up and running")
    @ApiResponse(responseCode = "200", description = "API is functional")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "message", "The AREA API is working correctly",
            "timestamp", java.time.Instant.now().toString()
        ));
    }

    @GetMapping("/swagger-test")
    @Operation(summary = "Swagger test", description = "Endpoint to verify that Swagger UI is working")
    @ApiResponse(responseCode = "200", description = "Swagger UI operational")
    public ResponseEntity<Map<String, String>> swaggerTest() {
        return ResponseEntity.ok(Map.of(
            "swagger", "OK",
            "openapi", "WORKING",
            "documentation", "AVAILABLE"
        ));
    }
}