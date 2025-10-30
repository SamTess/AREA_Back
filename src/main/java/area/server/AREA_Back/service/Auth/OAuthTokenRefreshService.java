package area.server.AREA_Back.service.Auth;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service for refreshing OAuth access tokens across different providers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenRefreshService {

    private static final int REFRESH_BUFFER_MINUTES = 5;

    private final TokenEncryptionService tokenEncryptionService;
    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final RestTemplate restTemplate;

    /**
     * Refresh Spotify access token
     */
    public boolean refreshSpotifyToken(UserOAuthIdentity identity, String clientId, String clientSecret) {
        return refreshToken(
            identity,
            "https://accounts.spotify.com/api/token",
            clientId,
            clientSecret,
            true
        );
    }

    /**
     * Refresh Google access token
     */
    public boolean refreshGoogleToken(UserOAuthIdentity identity, String clientId, String clientSecret) {
        return refreshToken(
            identity,
            "https://oauth2.googleapis.com/token",
            clientId,
            clientSecret,
            false
        );
    }

    /**
     * Refresh Discord access token
     */
    public boolean refreshDiscordToken(UserOAuthIdentity identity, String clientId, String clientSecret) {
        return refreshToken(
            identity,
            "https://discord.com/api/oauth2/token",
            clientId,
            clientSecret,
            false
        );
    }

    /**
     * Generic token refresh method
     */
    private boolean refreshToken(UserOAuthIdentity identity, String tokenUrl,
                                 String clientId, String clientSecret, boolean useBasicAuth) {
        try {
            String encryptedRefreshToken = identity.getRefreshTokenEnc();
            if (encryptedRefreshToken == null || encryptedRefreshToken.isEmpty()) {
                log.error("No refresh token available for user {} provider {}",
                         identity.getUser().getId(), identity.getProvider());
                return false;
            }

            String refreshToken = tokenEncryptionService.decryptToken(encryptedRefreshToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "refresh_token");
            body.add("refresh_token", refreshToken);

            if (useBasicAuth) {
                headers.setBasicAuth(clientId, clientSecret);
            } else {
                body.add("client_id", clientId);
                body.add("client_secret", clientSecret);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to refresh {} token: HTTP {}",
                         identity.getProvider(), response.getStatusCode());
                return false;
            }

            Map<String, Object> responseBody = response.getBody();
            String newAccessToken = (String) responseBody.get("access_token");

            if (newAccessToken == null) {
                log.error("No access_token in refresh response for {}", identity.getProvider());
                return false;
            }

            identity.setAccessTokenEnc(tokenEncryptionService.encryptToken(newAccessToken));

            String newRefreshToken = (String) responseBody.get("refresh_token");
            if (newRefreshToken != null && !newRefreshToken.isEmpty()) {
                identity.setRefreshTokenEnc(tokenEncryptionService.encryptToken(newRefreshToken));
            }

            Object expiresInObj = responseBody.get("expires_in");
            if (expiresInObj != null) {
                Integer expiresIn = null;
                if (expiresInObj instanceof Integer) {
                    expiresIn = (Integer) expiresInObj;
                } else {
                    try {
                        expiresIn = Integer.parseInt(expiresInObj.toString());
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse expires_in: {}", expiresInObj);
                    }
                }
                if (expiresIn != null) {
                    identity.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
                }
            }

            identity.setUpdatedAt(LocalDateTime.now());
            userOAuthIdentityRepository.save(identity);

            log.info("Successfully refreshed {} token for user {}",
                    identity.getProvider(), identity.getUser().getId());
            return true;
        } catch (Exception e) {
            log.error("Error refreshing {} token: {}",
                     identity.getProvider(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if token needs refresh (expired or about to expire within 5 minutes)
     */
    public boolean needsRefresh(UserOAuthIdentity identity) {
        LocalDateTime expiresAt = identity.getExpiresAt();
        if (expiresAt == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime refreshThreshold = expiresAt.minusMinutes(REFRESH_BUFFER_MINUTES);
        return now.isAfter(refreshThreshold) || now.isEqual(refreshThreshold);
    }
}
