package area.server.AREA_Back.service.Area;

import area.server.AREA_Back.dto.AreaDraftRequest;
import area.server.AREA_Back.dto.AreaDraftResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class AreaDraftCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String DRAFT_PREFIX = "area:draft:";
    private static final Duration DRAFT_TTL = Duration.ofHours(1);

    public String saveDraft(UUID userId, AreaDraftRequest draft) {
        return saveDraft(userId, draft, null);
    }

    public String saveDraft(UUID userId, AreaDraftRequest draft, UUID areaId) {
        try {
            String key;
            String draftId;

            if (areaId != null) {
                key = DRAFT_PREFIX + "edit:" + areaId + ":" + userId;
                draftId = areaId.toString();
                log.info("Saving edit draft for area: {} (userId: {})", areaId, userId);
            } else {
                draftId = draft.getDraftId() != null && !draft.getDraftId().isEmpty()
                    ? draft.getDraftId()
                    : UUID.randomUUID().toString();
                key = DRAFT_PREFIX + "new:" + userId + ":" + draftId;
                log.info("Saving new draft with draftId: {} (userId: {})", draftId, userId);
            }

            if (draft.getSavedAt() == null) {
                draft.setSavedAt(LocalDateTime.now());
            }

            log.info("Saving draft with key: {}", key);
            redisTemplate.opsForValue().set(key, draft, DRAFT_TTL);
            log.info("Draft saved successfully");

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
            String keyNew = DRAFT_PREFIX + "new:" + userId + ":" + draftId;
            Object draft = redisTemplate.opsForValue().get(keyNew);

            if (draft instanceof AreaDraftRequest) {
                AreaDraftRequest draftRequest = (AreaDraftRequest) draft;
                AreaDraftResponse response = convertToResponse(userId, draftId, draftRequest, keyNew);
                return Optional.of(response);
            }

            String keyEdit = DRAFT_PREFIX + "edit:" + draftId + ":" + userId;
            draft = redisTemplate.opsForValue().get(keyEdit);

            if (draft instanceof AreaDraftRequest) {
                AreaDraftRequest draftRequest = (AreaDraftRequest) draft;
                AreaDraftResponse response = convertToResponse(userId, draftId, draftRequest, keyEdit);
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
            String pattern = DRAFT_PREFIX + "new:" + userId + ":*";
            List<AreaDraftResponse> drafts = new ArrayList<>();

            log.info("Scanning for new area drafts with pattern: {}", pattern);

            redisTemplate.execute((RedisCallback<Void>) connection -> {
                org.springframework.data.redis.core.ScanOptions options =
                    org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match(pattern)
                        .count(100)
                        .build();

                org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.scan(options);

                int keyCount = 0;
                while (cursor.hasNext()) {
                    String key = new String(cursor.next());
                    keyCount++;
                    log.info("Found key #{}: {}", keyCount, key);

                    Object draft = redisTemplate.opsForValue().get(key);

                    if (draft instanceof AreaDraftRequest) {
                        String draftId = key.substring((DRAFT_PREFIX + "new:" + userId + ":").length());
                        log.info("Adding new area draft with ID: {}", draftId);
                        drafts.add(convertToResponse(userId, draftId, (AreaDraftRequest) draft, key));
                    } else {
                        log.warn("Draft is not an instance of AreaDraftRequest for key: {}", key);
                    }
                }

                log.info("Total keys found: {}, Drafts added: {}", keyCount, drafts.size());
                cursor.close();
                return null;
            });

            log.info("Returning {} new area drafts for user {}", drafts.size(), userId);
            return drafts;
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed while getting user drafts: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to get drafts for user: {}", userId, e);
            return Collections.emptyList();
        }
    }

    public Optional<AreaDraftResponse> getEditDraft(UUID userId, UUID areaId) {
        try {
            String key = DRAFT_PREFIX + "edit:" + areaId + ":" + userId;
            log.info("Getting edit draft with key: {}", key);

            Object draft = redisTemplate.opsForValue().get(key);

            if (draft instanceof AreaDraftRequest) {
                AreaDraftRequest draftRequest = (AreaDraftRequest) draft;
                AreaDraftResponse response = convertToResponse(userId, areaId.toString(), draftRequest, key);
                log.info("Edit draft found for area: {}", areaId);
                return Optional.of(response);
            }

            log.info("No edit draft found for area: {}", areaId);
            return Optional.empty();
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed while getting edit draft: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get edit draft for area: {} and user: {}", areaId, userId, e);
            return Optional.empty();
        }
    }

    public void deleteDraft(UUID userId, String draftId) {
        try {
            String keyNew = DRAFT_PREFIX + "new:" + userId + ":" + draftId;
            Boolean deletedNew = redisTemplate.delete(keyNew);

            String keyEdit = DRAFT_PREFIX + "edit:" + draftId + ":" + userId;
            Boolean deletedEdit = redisTemplate.delete(keyEdit);

            if (Boolean.TRUE.equals(deletedNew) || Boolean.TRUE.equals(deletedEdit)) {
                log.info("Draft deleted: {} for user: {}", draftId, userId);
            } else {
                log.warn("No draft found to delete: {} for user: {}", draftId, userId);
            }
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
            String keyNew = DRAFT_PREFIX + "new:" + userId + ":" + draftId;
            Boolean extendedNew = redisTemplate.expire(keyNew, DRAFT_TTL);

            String keyEdit = DRAFT_PREFIX + "edit:" + draftId + ":" + userId;
            Boolean extendedEdit = redisTemplate.expire(keyEdit, DRAFT_TTL);

            if (Boolean.TRUE.equals(extendedNew) || Boolean.TRUE.equals(extendedEdit)) {
                log.debug("Draft TTL extended: {} for user: {}", draftId, userId);
            } else {
                log.warn("No draft found to extend TTL: {} for user: {}", draftId, userId);
            }
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
        response.setSavedAt(draft.getSavedAt() != null ? draft.getSavedAt() : LocalDateTime.now());

        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            response.setTtlSeconds(ttl != null ? ttl : 0L);
        } catch (Exception e) {
            response.setTtlSeconds(0L);
        }

        return response;
    }
}
