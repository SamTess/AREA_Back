package area.server.AREA_Back.service.Area;

import area.server.AREA_Back.dto.AreaDraftRequest;
import area.server.AREA_Back.dto.AreaDraftResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AreaDraftCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String DRAFT_PREFIX = "area:draft:";
    private static final Duration DRAFT_TTL = Duration.ofHours(1);

    public String saveDraft(UUID userId, AreaDraftRequest draft) {
        try {
            String draftId = draft.getDraftId() != null && !draft.getDraftId().isEmpty()
                ? draft.getDraftId()
                : UUID.randomUUID().toString();

            String key = DRAFT_PREFIX + userId + ":" + draftId;

            redisTemplate.opsForValue().set(key, draft, DRAFT_TTL);
            log.info("Draft saved: {} for user: {}", draftId, userId);

            return draftId;
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed while saving draft: {}", e.getMessage());
            throw new RuntimeException("Cache service unavailable", e);
        } catch (Exception e) {
            log.error("Failed to save draft for user: {}", userId, e);
            throw new RuntimeException("Failed to save draft", e);
        }
    }

    public Optional<AreaDraftResponse> getDraft(UUID userId, String draftId) {
        try {
            String key = DRAFT_PREFIX + userId + ":" + draftId;
            Object draft = redisTemplate.opsForValue().get(key);

            if (draft instanceof AreaDraftRequest) {
                AreaDraftRequest draftRequest = (AreaDraftRequest) draft;
                AreaDraftResponse response = convertToResponse(userId, draftId, draftRequest, key);
                return Optional.of(response);
            }
            return Optional.empty();
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed while getting draft: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get draft: {} for user: {}", draftId, userId, e);
            return Optional.empty();
        }
    }

    public List<AreaDraftResponse> getUserDrafts(UUID userId) {
        try {
            String pattern = DRAFT_PREFIX + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);

            if (keys == null || keys.isEmpty()) {
                return Collections.emptyList();
            }

            return keys.stream()
                .map(key -> {
                    Object draft = redisTemplate.opsForValue().get(key);
                    if (draft instanceof AreaDraftRequest) {
                        String draftId = key.substring((DRAFT_PREFIX + userId + ":").length());
                        return convertToResponse(userId, draftId, (AreaDraftRequest) draft, key);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed while getting user drafts: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get drafts for user: {}", userId, e);
            return Collections.emptyList();
        }
    }

    public void deleteDraft(UUID userId, String draftId) {
        try {
            String key = DRAFT_PREFIX + userId + ":" + draftId;
            redisTemplate.delete(key);
            log.info("Draft deleted: {} for user: {}", draftId, userId);
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed while deleting draft: {}", e.getMessage());
            throw new RuntimeException("Cache service unavailable", e);
        } catch (Exception e) {
            log.error("Failed to delete draft: {} for user: {}", draftId, userId, e);
            throw new RuntimeException("Failed to delete draft", e);
        }
    }

    public void extendDraftTTL(UUID userId, String draftId) {
        try {
            String key = DRAFT_PREFIX + userId + ":" + draftId;
            redisTemplate.expire(key, DRAFT_TTL);
            log.debug("Draft TTL extended: {} for user: {}", draftId, userId);
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed while extending draft TTL: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to extend draft TTL: {} for user: {}", draftId, userId, e);
        }
    }

    public boolean isCacheAvailable() {
        try {
            redisTemplate.opsForValue().get("test:connection");
            return true;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis cache is not available: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Cache availability check failed: {}", e.getMessage());
            return false;
        }
    }

    private AreaDraftResponse convertToResponse(UUID userId, String draftId, AreaDraftRequest draft, String key) {
        AreaDraftResponse response = new AreaDraftResponse();
        response.setDraftId(draftId);
        response.setName(draft.getName());
        response.setDescription(draft.getDescription());
        response.setUserId(userId);
        response.setActions(draft.getActions());
        response.setReactions(draft.getReactions());
        response.setConnections(draft.getConnections());
        response.setLayoutMode(draft.getLayoutMode());
        response.setSavedAt(LocalDateTime.now());

        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            response.setTtlSeconds(ttl != null ? ttl : 0L);
        } catch (Exception e) {
            response.setTtlSeconds(0L);
        }

        return response;
    }
}
