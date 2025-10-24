package area.server.AREA_Back.service.Auth;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserLocalIdentityRepository;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserIdentityService {

    private final UserLocalIdentityRepository userLocalIdentityRepository;
    private final UserOAuthIdentityRepository userOAuthIdentityRepository;

    /**
     * Check if user has local identity (email/password)
     */
    public boolean hasLocalIdentity(UUID userId) {
        return userLocalIdentityRepository.findByUserId(userId)
                .map(identity -> {
                    String hash = identity.getPasswordHash();
                    boolean hasValidPassword = hash != null
                                              && !hash.isEmpty()
                                              && hash.startsWith("$2");
                    log.debug("User {} hasLocalIdentity check: passwordHash exists={}, isValidBcrypt={}",
                            userId, hash != null, hasValidPassword);
                    return hasValidPassword;
                })
                .orElse(false);
    }

    /**
     * Check if user has OAuth identity for a specific provider
     */
    public boolean hasOAuthIdentity(UUID userId, String provider) {
        return userOAuthIdentityRepository.findByUserIdAndProvider(userId, provider).isPresent();
    }

    /**
     * Get all OAuth identities for a user
     */
    public List<UserOAuthIdentity> getUserOAuthIdentities(UUID userId) {
        return userOAuthIdentityRepository.findByUserId(userId);
    }

    /**
     * Get OAuth identity for a specific provider
     */
    public Optional<UserOAuthIdentity> getOAuthIdentity(UUID userId, String provider) {
        return userOAuthIdentityRepository.findByUserIdAndProvider(userId, provider);
    }

    /**
     * Check if user needs to connect to a service for the first time
     * Returns true if user doesn't have OAuth identity for the provider
     */
    public boolean needsServiceConnection(UUID userId, String provider) {
        return !hasOAuthIdentity(userId, provider);
    }

    /**
     * Get user creation method (LOCAL, OAUTH, or BOTH)
     */
    public UserCreationType getUserCreationType(UUID userId) {
        boolean hasLocal = hasLocalIdentity(userId);
        List<UserOAuthIdentity> oauthIdentities = getUserOAuthIdentities(userId);
        boolean hasOAuth = !oauthIdentities.isEmpty();

        if (hasLocal && hasOAuth) {
            return UserCreationType.BOTH;
        } else if (hasLocal) {
            return UserCreationType.LOCAL;
        } else if (hasOAuth) {
            return UserCreationType.OAUTH;
        } else {
            return UserCreationType.UNKNOWN;
        }
    }

    /**
     * Get the primary OAuth provider for a user (first one created)
     */
    public Optional<String> getPrimaryOAuthProvider(UUID userId) {
        List<UserOAuthIdentity> identities = getUserOAuthIdentities(userId);
        return identities.stream()
                .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(UserOAuthIdentity::getProvider);
    }

    public boolean canDisconnectService(UUID userId, String provider) {
        Optional<String> primaryProvider = getPrimaryOAuthProvider(userId);

        log.debug("Checking canDisconnect for user {} provider {}: primaryProvider={}",
                userId, provider, primaryProvider.orElse("none"));

        if (primaryProvider.isPresent() && primaryProvider.get().equalsIgnoreCase(provider)) {
            log.debug("Provider {} is primary, cannot disconnect", provider);
            return false;
        }

        log.debug("Provider {} is not primary, can disconnect: true", provider);
        return true;
    }

    public void disconnectService(UUID userId, String provider) {
        if (!canDisconnectService(userId, provider)) {
            throw new IllegalStateException("Cannot disconnect primary authentication provider");
        }
        userOAuthIdentityRepository.deleteByUserIdAndProvider(userId, provider);
    }

    public enum UserCreationType {
        LOCAL,
        OAUTH,
        BOTH,
        UNKNOWN
    }
}