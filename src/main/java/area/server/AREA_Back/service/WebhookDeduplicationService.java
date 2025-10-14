package area.server.AREA_Back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service for webhook event deduplication using Redis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDeduplicationService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String WEBHOOK_DEDUP_PREFIX = "webhook:dedup:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);
    private static final Duration GITHUB_TTL = Duration.ofMinutes(30);
    private static final Duration SLACK_TTL = Duration.ofMinutes(5);
    private static final Duration GENERIC_TTL = Duration.ofMinutes(15);
    private static final int SCAN_COUNT = 100;

    /**
     * Checks if an event has already been processed (is a duplicate)
     *
     * @param eventId Unique event identifier
     * @param provider The webhook provider (github, slack, etc.)
     * @return true if event is a duplicate, false if it's new
     */
    public boolean isDuplicate(String eventId, String provider) {
        if (eventId == null || eventId.trim().isEmpty()) {
            log.warn("Event ID is null or empty, considering as duplicate");
            return true;
        }

        String key = buildDeduplicationKey(eventId, provider);

        try {
            Boolean exists = redisTemplate.hasKey(key);
            boolean duplicate = Boolean.TRUE.equals(exists);

            return duplicate;
        } catch (Exception e) {
            log.error("Error checking event duplication for {}: {}", eventId, e.getMessage());
            return false;
        }
    }

    /**
     * Marks an event as processed
     *
     * @param eventId Unique event identifier
     * @param provider The webhook provider
     */
    public void markAsProcessed(String eventId, String provider) {
        markAsProcessed(eventId, provider, null);
    }

    /**
     * Marks an event as processed with custom TTL
     *
     * @param eventId Unique event identifier
     * @param provider The webhook provider
     * @param customTtl Custom TTL duration, null to use provider default
     */
    public void markAsProcessed(String eventId, String provider, Duration customTtl) {
        if (eventId == null || eventId.trim().isEmpty()) {
            log.warn("Cannot mark null or empty event ID as processed");
            return;
        }

        String key = buildDeduplicationKey(eventId, provider);
        Duration ttl;
        if (customTtl != null) {
            ttl = customTtl;
        } else {
            ttl = getTtlForProvider(provider);
        }

        try {
            redisTemplate.opsForValue().set(key, System.currentTimeMillis(), ttl);
        } catch (Exception e) {
            log.error("Error marking event {} as processed: {}", eventId, e.getMessage());
        }
    }

    /**
     * Checks if an event is a duplicate and marks it as processed if it's new
     * This is an atomic operation to prevent race conditions
     *
     * @param eventId Unique event identifier
     * @param provider The webhook provider
     * @return true if event was already processed, false if it's new (and now marked as processed)
     */
    public boolean checkAndMark(String eventId, String provider) {
        return checkAndMark(eventId, provider, null);
    }

    /**
     * Checks if an event is a duplicate and marks it as processed if it's new
     * This is an atomic operation to prevent race conditions
     *
     * @param eventId Unique event identifier
     * @param provider The webhook provider
     * @param customTtl Custom TTL duration, null to use provider default
     * @return true if event was already processed, false if it's new (and now marked as processed)
     */
    public boolean checkAndMark(String eventId, String provider, Duration customTtl) {
        if (eventId == null || eventId.trim().isEmpty()) {
            log.warn("Event ID is null or empty, considering as duplicate");
            return true;
        }

        String key = buildDeduplicationKey(eventId, provider);
        Duration ttl;
        if (customTtl != null) {
            ttl = customTtl;
        } else {
            ttl = getTtlForProvider(provider);
        }

        try {
            Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(key, System.currentTimeMillis(), ttl);
            boolean isNew = Boolean.TRUE.equals(wasSet);

            return !isNew;
        } catch (Exception e) {
            log.error("Error in atomic check-and-mark for event {}: {}", eventId, e.getMessage());
            return false;
        }
    }

    /**
     * Removes an event from the deduplication cache
     * Useful for testing or manual cleanup
     *
     * @param eventId Unique event identifier
     * @param provider The webhook provider
     */
    public void removeEvent(String eventId, String provider) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return;
        }

        String key = buildDeduplicationKey(eventId, provider);

        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Error removing event {} from deduplication cache: {}", eventId, e.getMessage());
        }
    }

    /**
     * Gets the remaining TTL for an event
     *
     * @param eventId Unique event identifier
     * @param provider The webhook provider
     * @return remaining TTL in seconds, -1 if key doesn't exist, -2 if key exists but has no TTL
     */
    public long getRemainingTtl(String eventId, String provider) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return -1;
        }

        String key = buildDeduplicationKey(eventId, provider);

        try {
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error getting TTL for event {}: {}", eventId, e.getMessage());
            return -1;
        }
    }

    /**
     * Clears all deduplication entries for a specific provider
     * Useful for maintenance or testing
     * SECURITY: Uses SCAN instead of KEYS to avoid blocking Redis
     *
     * @param provider The webhook provider
     */
    public void clearProviderEvents(String provider) {
        try {
            String pattern = WEBHOOK_DEDUP_PREFIX + provider + ":*";
            Set<String> keysToDelete = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                Cursor<byte[]> cursor = connection.scan(options);
                while (cursor.hasNext()) {
                    keysToDelete.add(new String(cursor.next()));
                }
                try {
                    cursor.close();
                } catch (Exception e) {
                    log.warn("Error closing cursor: {}", e.getMessage());
                }
                return null;
            });
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
            }
        } catch (Exception e) {
            log.error("Error clearing events for provider {}: {}", provider, e.getMessage());
        }
    }

    /**
     * Builds the Redis key for deduplication
     */
    private String buildDeduplicationKey(String eventId, String provider) {
        return WEBHOOK_DEDUP_PREFIX + provider.toLowerCase() + ":" + eventId;
    }

    /**
     * Gets the appropriate TTL for a provider
     */
    private Duration getTtlForProvider(String provider) {
        if (provider == null) {
            return DEFAULT_TTL;
        }

        switch (provider.toLowerCase()) {
            case "github":
                return GITHUB_TTL;
            case "slack":
                return SLACK_TTL;
            default:
                return GENERIC_TTL;
        }
    }
}