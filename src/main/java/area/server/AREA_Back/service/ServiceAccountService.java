package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.Service;
import area.server.AREA_Back.entity.ServiceAccount;
import area.server.AREA_Back.entity.User;
import area.server.AREA_Back.repository.ServiceAccountRepository;
import area.server.AREA_Back.repository.ServiceRepository;
import area.server.AREA_Back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ServiceAccountService {

    private final ServiceAccountRepository serviceAccountRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TokenEncryptionService tokenEncryptionService;

    /**
     * Create or update a service account with token
     */
    public ServiceAccount createOrUpdateServiceAccount(UUID userId, String serviceKey, String accessToken,
                                                     String refreshToken, LocalDateTime expiresAt,
                                                     Map<String, Object> scopes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Service service = serviceRepository.findByKey(serviceKey)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceKey));

        Optional<ServiceAccount> existingAccount = serviceAccountRepository.findByUserAndService(user, service);

        ServiceAccount serviceAccount;
        if (existingAccount.isPresent()) {
            serviceAccount = existingAccount.get();
            log.info("Updating existing service account for user {} and service {}", userId, serviceKey);
        } else {
            serviceAccount = new ServiceAccount();
            serviceAccount.setUser(user);
            serviceAccount.setService(service);
            log.info("Creating new service account for user {} and service {}", userId, serviceKey);
        }

        if (accessToken != null && !accessToken.trim().isEmpty()) {
            serviceAccount.setAccessTokenEnc(tokenEncryptionService.encryptToken(accessToken));
        }

        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            serviceAccount.setRefreshTokenEnc(tokenEncryptionService.encryptToken(refreshToken));
        }

        serviceAccount.setExpiresAt(expiresAt);
        serviceAccount.setScopes(scopes);
        serviceAccount.setLastRefreshAt(LocalDateTime.now());
        serviceAccount.setRevokedAt(null);

        Integer currentVersion = serviceAccount.getTokenVersion();
        if (currentVersion != null) {
            serviceAccount.setTokenVersion(currentVersion + 1);
        } else {
            serviceAccount.setTokenVersion(1);
        }

        return serviceAccountRepository.save(serviceAccount);
    }

    /**
     * Get service account by user and service key
     */
    public Optional<ServiceAccount> getServiceAccount(UUID userId, String serviceKey) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            Service service = serviceRepository.findByKey(serviceKey)
                    .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceKey));

            return serviceAccountRepository.findByUserAndService(user, service)
                    .filter(account -> account.getRevokedAt() == null);
        } catch (Exception e) {
            log.error("Error getting service account for user {} and service {}: {}",
                     userId, serviceKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get decrypted access token for a service
     */
    public Optional<String> getAccessToken(UUID userId, String serviceKey) {
        return getServiceAccount(userId, serviceKey)
                .map(account -> {
                    if (account.getAccessTokenEnc() == null) {
                        return null;
                    }
                    try {
                        return tokenEncryptionService.decryptToken(account.getAccessTokenEnc());
                    } catch (Exception e) {
                        log.error("Failed to decrypt access token for user {} and service {}: {}",
                                 userId, serviceKey, e.getMessage());
                        return null;
                    }
                });
    }

    /**
     * Get all service accounts for a user
     */
    public List<ServiceAccount> getUserServiceAccounts(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return serviceAccountRepository.findActiveByUser(user);
    }

    /**
     * Revoke (disable) a service account
     */
    public void revokeServiceAccount(UUID userId, String serviceKey) {
        getServiceAccount(userId, serviceKey)
                .ifPresent(account -> {
                    account.setRevokedAt(LocalDateTime.now());
                    serviceAccountRepository.save(account);
                    log.info("Revoked service account for user {} and service {}", userId, serviceKey);
                });
    }

    /**
     * Check if user has access token for a service
     */
    public boolean hasValidToken(UUID userId, String serviceKey) {
        return getServiceAccount(userId, serviceKey)
                .map(account -> account.getAccessTokenEnc() != null
                              && (account.getExpiresAt() == null
                              || account.getExpiresAt().isAfter(LocalDateTime.now())))
                .orElse(false);
    }

    /**
     * Store additional service-specific token metadata
     */
    public void storeTokenMetadata(UUID userId, String serviceKey, String remoteAccountId,
                                 String webhookSecret, Map<String, Object> additionalScopes) {
        getServiceAccount(userId, serviceKey)
                .ifPresent(account -> {
                    if (remoteAccountId != null) {
                        account.setRemoteAccountId(remoteAccountId);
                    }
                    if (webhookSecret != null) {
                        account.setWebhookSecretEnc(tokenEncryptionService.encryptToken(webhookSecret));
                    }
                    if (additionalScopes != null) {
                        Map<String, Object> currentScopes = account.getScopes();
                        if (currentScopes != null) {
                            currentScopes.putAll(additionalScopes);
                        } else {
                            account.setScopes(additionalScopes);
                        }
                    }
                    serviceAccountRepository.save(account);
                });
    }
}