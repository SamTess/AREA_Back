package area.server.AREA_Back.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service to store PKCE code challenges temporarily
 * Stores code_challenge indexed by state parameter
 * Auto-expires entries after 10 minutes
 */
@Service
public class PKCEStore {
    
    private static class PKCEEntry {
        String codeChallenge;
        String codeChallengeMethod;
        long expiresAt;
        
        PKCEEntry(String codeChallenge, String codeChallengeMethod) {
            this.codeChallenge = codeChallenge;
            this.codeChallengeMethod = codeChallengeMethod;
            this.expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
    
    private final ConcurrentHashMap<String, PKCEEntry> store = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    
    public PKCEStore() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * Store PKCE challenge for a state
     */
    public void store(String state, String codeChallenge, String codeChallengeMethod) {
        store.put(state, new PKCEEntry(codeChallenge, codeChallengeMethod));
    }
    
    /**
     * Retrieve and remove PKCE challenge for a state
     * Returns null if not found or expired
     */
    public String retrieve(String state) {
        PKCEEntry entry = store.remove(state);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.codeChallenge;
    }
    
    /**
     * Get PKCE method for a state (without removing)
     */
    public String getMethod(String state) {
        PKCEEntry entry = store.get(state);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.codeChallengeMethod;
    }
    
    /**
     * Cleanup expired entries
     */
    private void cleanup() {
        store.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
