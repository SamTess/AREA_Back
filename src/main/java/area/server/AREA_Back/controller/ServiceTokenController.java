package area.server.AREA_Back.controller;

import area.server.AREA_Back.constants.AuthTokenConstants;
import area.server.AREA_Back.dto.ServiceTokenResponse;
import area.server.AREA_Back.dto.ServiceTokenStatusResponse;
import area.server.AREA_Back.dto.StoreTokenRequest;
import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.service.JwtService;
import area.server.AREA_Back.service.ServiceAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/service-tokens")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Service Tokens", description = "Service token management operations")
public class ServiceTokenController {

    private final ServiceAccountService serviceAccountService;
    private final JwtService jwtService;

    @GetMapping("/{serviceName}/status")
    @Operation(summary = "Check if user has a valid token for a service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token status retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ServiceTokenStatusResponse> hasValidToken(
            @PathVariable String serviceName,
            HttpServletRequest httpRequest) {
        try {
            log.debug("Checking token status for service: {}", serviceName);
            UUID userId = getUserIdFromRequest(httpRequest);

            boolean hasValidToken = serviceAccountService.hasValidToken(userId, serviceName);

            ServiceTokenStatusResponse response = new ServiceTokenStatusResponse();
            response.setHasValidToken(hasValidToken);
            response.setServiceName(serviceName);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error checking token status for service {}: {}", serviceName, e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/{serviceName}")
    @Operation(summary = "Store a service token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token stored successfully"),
        @ApiResponse(responseCode = "400", description = "Bad request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ServiceTokenResponse> storeToken(
            @PathVariable String serviceName,
            @RequestBody StoreTokenRequest tokenData,
            HttpServletRequest httpRequest) {
        try {
            log.debug("Storing token for service: {}", serviceName);
            UUID userId = getUserIdFromRequest(httpRequest);

            ServiceAccount serviceAccount = serviceAccountService.createOrUpdateServiceAccount(
                userId,
                serviceName,
                tokenData.getAccessToken(),
                tokenData.getRefreshToken(),
                tokenData.getExpiresAt(),
                tokenData.getScopes()
            );

            ServiceTokenResponse response = convertToDto(serviceAccount, serviceName);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error storing token for service {}: {}", serviceName, e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/{serviceName}")
    @Operation(summary = "Get service token information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token information retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Token not found")
    })
    public ResponseEntity<ServiceTokenResponse> getToken(
            @PathVariable String serviceName,
            HttpServletRequest httpRequest) {
        try {
            log.debug("Getting token for service: {}", serviceName);
            UUID userId = getUserIdFromRequest(httpRequest);

            Optional<ServiceAccount> serviceAccountOpt = serviceAccountService.getServiceAccount(userId, serviceName);
            if (serviceAccountOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            ServiceTokenResponse dto = convertToDto(serviceAccountOpt.get(), serviceName);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("Error getting token for service {}: {}", serviceName, e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    @DeleteMapping("/{serviceName}")
    @Operation(summary = "Delete a service token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Token not found")
    })
    public ResponseEntity<Void> deleteToken(
            @PathVariable String serviceName,
            HttpServletRequest httpRequest) {
        try {
            log.debug("Deleting token for service: {}", serviceName);
            UUID userId = getUserIdFromRequest(httpRequest);

            Optional<ServiceAccount> serviceAccount = serviceAccountService.getServiceAccount(userId, serviceName);
            if (serviceAccount.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            serviceAccountService.revokeServiceAccount(userId, serviceName);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error deleting token for service {}: {}", serviceName, e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping
    @Operation(summary = "Get all service tokens for the authenticated user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service tokens retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<ServiceTokenResponse>> getAllTokens(HttpServletRequest httpRequest) {
        try {
            log.debug("Getting all tokens for user");
            UUID userId = getUserIdFromRequest(httpRequest);

            List<ServiceAccount> serviceAccounts = serviceAccountService.getUserServiceAccounts(userId);
            List<ServiceTokenResponse> dtos = serviceAccounts.stream()
                    .map(account -> convertToDto(account, account.getService().getKey()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Error getting all tokens: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    private ServiceTokenResponse convertToDto(ServiceAccount serviceAccount, String serviceName) {
        ServiceTokenResponse dto = new ServiceTokenResponse();
        dto.setId(serviceAccount.getId().toString());
        dto.setServiceKey(serviceAccount.getService().getKey());
        dto.setServiceName(serviceName);
        dto.setRemoteAccountId(serviceAccount.getRemoteAccountId());
        dto.setHasAccessToken(serviceAccount.getAccessTokenEnc() != null);
        dto.setHasRefreshToken(serviceAccount.getRefreshTokenEnc() != null);
        dto.setExpiresAt(serviceAccount.getExpiresAt());
        dto.setExpired(serviceAccount.getExpiresAt() != null && serviceAccount.getExpiresAt().isBefore(LocalDateTime.now()));
        dto.setScopes(serviceAccount.getScopes());
        dto.setTokenVersion(serviceAccount.getTokenVersion());
        dto.setLastRefreshAt(serviceAccount.getLastRefreshAt());
        dto.setCreatedAt(serviceAccount.getCreatedAt());
        dto.setUpdatedAt(serviceAccount.getUpdatedAt());
        return dto;
    }

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        try {
            String accessToken = extractAccessTokenFromCookies(request);
            if (accessToken == null) {
                throw new RuntimeException("Access token not found in cookies");
            }

            return jwtService.extractUserIdFromAccessToken(accessToken);
        } catch (Exception e) {
            log.error("Failed to extract user ID from request: {}", e.getMessage());
            throw new RuntimeException("Failed to extract user ID", e);
        }
    }

    private String extractAccessTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (AuthTokenConstants.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}