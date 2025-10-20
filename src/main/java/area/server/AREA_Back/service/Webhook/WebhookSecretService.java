package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.ServiceAccountRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing webhook secrets for different services
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookSecretService {

    private final ServiceAccountRepository serviceAccountRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;

    @Value("${app.webhook.github.secret:#{null}}")
    private String githubWebhookSecret;

    @Value("${app.webhook.slack.secret:#{null}}")
    private String slackWebhookSecret;

    @Value("${app.webhook.discord.secret:#{null}}")
    private String discordWebhookSecret;

    // Cache for service secrets
    private final Map<String, String> secretCache = new HashMap<>();

    /**
     * Gets the webhook secret for a service (global configuration)
     *
     * @param service The service name (github, slack, discord, etc.)
     * @return The webhook secret or null if not configured
     */
    public String getServiceSecret(String service) {
        if (service == null) {
            return null;
        }

        // Check cache first
        if (secretCache.containsKey(service.toLowerCase())) {
            return secretCache.get(service.toLowerCase());
        }

        String secret = switch (service.toLowerCase()) {
            case "github" -> githubWebhookSecret;
            case "slack" -> slackWebhookSecret;
            case "discord" -> discordWebhookSecret;
            default -> null;
        };

        // Cache the result
        if (secret != null) {
            secretCache.put(service.toLowerCase(), secret);
        }

        return secret;
    }

    /**
     * Gets the webhook secret for a specific user's service account
     * This allows per-user webhook secrets if configured
     *
     * @param service The service name
     * @param userId The user ID
     * @return The webhook secret or null if not configured
     */
    public String getUserServiceSecret(String service, UUID userId) {
        if (service == null || userId == null) {
            return null;
        }

        try {
            // Try to get from service account
            Optional<User> user = userRepository.findById(userId);
            Optional<area.server.AREA_Back.entity.Service> serviceEntity = 
                    serviceRepository.findByKey(service.toLowerCase());

            if (user.isPresent() && serviceEntity.isPresent()) {
                Optional<ServiceAccount> serviceAccount = serviceAccountRepository
                        .findByUserAndService(user.get(), serviceEntity.get());

                if (serviceAccount.isPresent() && serviceAccount.get().getWebhookSecretEnc() != null) {
                    log.debug("Using per-user webhook secret for service {} and user {}", service, userId);
                    // TODO: Decrypt the webhook secret using encryption service
                    return serviceAccount.get().getWebhookSecretEnc();
                }
            }
        } catch (Exception e) {
            log.warn("Error retrieving user service secret for service {} and user {}: {}",
                    service, userId, e.getMessage());
        }

        // Fallback to global service secret
        return getServiceSecret(service);
    }

    /**
     * Checks if a service has a webhook secret configured
     *
     * @param service The service name
     * @return true if a secret is configured, false otherwise
     */
    public boolean hasServiceSecret(String service) {
        return getServiceSecret(service) != null;
    }

    /**
     * Checks if a user has a specific webhook secret configured for a service
     *
     * @param service The service name
     * @param userId The user ID
     * @return true if a secret is configured, false otherwise
     */
    public boolean hasUserServiceSecret(String service, UUID userId) {
        return getUserServiceSecret(service, userId) != null;
    }

    /**
     * Clears the secret cache (useful for testing or config reload)
     */
    public void clearCache() {
        secretCache.clear();
        log.info("Webhook secret cache cleared");
    }
}
