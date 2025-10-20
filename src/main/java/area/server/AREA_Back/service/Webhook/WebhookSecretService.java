package area.server.AREA_Back.service.Webhook;

import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.ServiceAccountRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.repository.UserRepository;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookSecretService {

    private final ServiceAccountRepository serviceAccountRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final TokenEncryptionService tokenEncryptionService;

    @Value("${app.webhook.github.secret:#{null}}")
    private String githubWebhookSecret;

    @Value("${app.webhook.slack.secret:#{null}}")
    private String slackWebhookSecret;

    @Value("${app.webhook.discord.secret:#{null}}")
    private String discordWebhookSecret;

    private final Map<String, String> secretCache = new HashMap<>();

    public String getServiceSecret(String service) {
        if (service == null) {
            return null;
        }

        if (secretCache.containsKey(service.toLowerCase())) {
            return secretCache.get(service.toLowerCase());
        }

        String secret = switch (service.toLowerCase()) {
            case "github" -> githubWebhookSecret;
            case "slack" -> slackWebhookSecret;
            case "discord" -> discordWebhookSecret;
            default -> null;
        };

        if (secret != null) {
            secretCache.put(service.toLowerCase(), secret);
        }

        return secret;
    }

    public String getUserServiceSecret(String service, UUID userId) {
        if (service == null || userId == null) {
            return null;
        }

        try {
            Optional<User> user = userRepository.findById(userId);
            Optional<area.server.AREA_Back.entity.Service> serviceEntity =
                    serviceRepository.findByKey(service.toLowerCase());

            if (user.isPresent() && serviceEntity.isPresent()) {
                Optional<ServiceAccount> serviceAccount = serviceAccountRepository
                        .findByUserAndService(user.get(), serviceEntity.get());

                if (serviceAccount.isPresent() && serviceAccount.get().getWebhookSecretEnc() != null) {
                    log.debug("Using per-user webhook secret for service {} and user {}", service, userId);
                    try {
                        String decryptedSecret = tokenEncryptionService.decryptToken(
                                serviceAccount.get().getWebhookSecretEnc()
                        );
                        return decryptedSecret;
                    } catch (Exception decryptException) {
                        log.error("Failed to decrypt webhook secret for service {} and user {}: {}",
                                service, userId, decryptException.getMessage());
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error retrieving user service secret for service {} and user {}: {}",
                    service, userId, e.getMessage());
        }
        return getServiceSecret(service);
    }

    public boolean hasServiceSecret(String service) {
        return getServiceSecret(service) != null;
    }

    public boolean hasUserServiceSecret(String service, UUID userId) {
        return getUserServiceSecret(service, userId) != null;
    }

    public void clearCache() {
        secretCache.clear();
        log.info("Webhook secret cache cleared");
    }
}
