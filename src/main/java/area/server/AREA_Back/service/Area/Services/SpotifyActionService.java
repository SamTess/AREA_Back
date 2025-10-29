package area.server.AREA_Back.service.Area.Services;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpotifyActionService {

    private static final String SPOTIFY_API_BASE = "https://api.spotify.com/v1";
    private static final String SPOTIFY_PROVIDER_KEY = "spotify";
    private static final int ISO_DATE_LENGTH = 19;
    private static final int MAX_VOLUME = 100;

    private final UserOAuthIdentityRepository userOAuthIdentityRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;
    private final area.server.AREA_Back.service.Auth.OAuthTokenRefreshService oauthTokenRefreshService;

    @org.springframework.beans.factory.annotation.Value(
        "${spring.security.oauth2.client.registration.spotify.client-id}")
    private String spotifyClientId;

    @org.springframework.beans.factory.annotation.Value(
        "${spring.security.oauth2.client.registration.spotify.client-secret}")
    private String spotifyClientSecret;

    private Counter spotifyActionsExecuted;
    private Counter spotifyActionsFailed;
    private Counter spotifyEventsChecked;

    private final Map<UUID, String> lastKnownTrackIds = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lastKnownPlaybackStates = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        spotifyActionsExecuted = meterRegistry.counter("spotify_actions_executed_total");
        spotifyActionsFailed = meterRegistry.counter("spotify_actions_failed_total");
        spotifyEventsChecked = meterRegistry.counter("spotify_events_checked_total");
        log.info("SpotifyActionService initialized with metrics");
    }

    /**
     * Execute a Spotify action (reaction)
     */
    public Map<String, Object> executeSpotifyAction(String actionKey,
                                                    Map<String, Object> inputPayload,
                                                    Map<String, Object> actionParams,
                                                    UUID userId) {
        try {
            spotifyActionsExecuted.increment();

            String spotifyToken = getSpotifyToken(userId);
            if (spotifyToken == null) {
                throw new RuntimeException("No Spotify token found for user: " + userId);
            }

            switch (actionKey) {
                case "play_track":
                    return playTrack(spotifyToken, inputPayload, actionParams);
                case "pause_playback":
                    return pausePlayback(spotifyToken);
                case "skip_to_next":
                    return skipToNext(spotifyToken);
                case "skip_to_previous":
                    return skipToPrevious(spotifyToken);
                case "add_to_queue":
                    return addToQueue(spotifyToken, inputPayload, actionParams);
                case "save_track":
                    return saveTrack(spotifyToken, inputPayload, actionParams);
                case "create_playlist":
                    return createPlaylist(spotifyToken, inputPayload, actionParams);
                case "add_to_playlist":
                    return addToPlaylist(spotifyToken, inputPayload, actionParams);
                case "set_volume":
                    return setVolume(spotifyToken, inputPayload, actionParams);
                case "set_repeat_mode":
                    return setRepeatMode(spotifyToken, inputPayload, actionParams);
                default:
                    throw new IllegalArgumentException("Unknown Spotify action: " + actionKey);
            }
        } catch (Exception e) {
            spotifyActionsFailed.increment();
            log.error("Failed to execute Spotify action {}: {}", actionKey, e.getMessage(), e);
            throw new RuntimeException("Spotify action execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check for Spotify events (triggers)
     */
    public List<Map<String, Object>> checkSpotifyEvents(String actionKey,
                                                        Map<String, Object> actionParams,
                                                        UUID userId,
                                                        LocalDateTime lastCheck) {
        try {
            spotifyEventsChecked.increment();
            log.debug("Checking Spotify events: {} for user: {}", actionKey, userId);

            String spotifyToken = getSpotifyToken(userId);
            if (spotifyToken == null) {
                log.warn("No Spotify token found for user: {}", userId);
                return Collections.emptyList();
            }

            // Add userId to params for state tracking
            Map<String, Object> enrichedParams = new HashMap<>(actionParams);
            enrichedParams.put("_internal_user_id", userId);

            switch (actionKey) {
                case "new_saved_track":
                    return checkNewSavedTracks(spotifyToken, enrichedParams, lastCheck);
                case "playback_started":
                    return checkPlaybackStarted(spotifyToken, enrichedParams, lastCheck);
                case "track_changed":
                    return checkTrackChanged(spotifyToken, enrichedParams, lastCheck);
                case "playlist_updated":
                    return checkPlaylistUpdated(spotifyToken, enrichedParams, lastCheck);
                default:
                    log.warn("Unknown Spotify trigger: {}", actionKey);
                    return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to check Spotify events {}: {}", actionKey, e.getMessage(), e);
            return Collections.emptyList();
        }
    }


    /**
     * Trigger: Check for newly saved tracks
     */
    private List<Map<String, Object>> checkNewSavedTracks(String token,
                                                          Map<String, Object> params,
                                                          LocalDateTime lastCheck) {
        try {
            String url = SPOTIFY_API_BASE + "/me/tracks?limit=50";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Failed to fetch saved tracks");
                return Collections.emptyList();
            }

            List<Map<String, Object>> events = new ArrayList<>();
            Map<String, Object> body = response.getBody();
            Object itemsObj = body.get("items");
            List<Map<String, Object>> items = new ArrayList<>();
            if (itemsObj instanceof List<?>) {
                List<?> rawList = (List<?>) itemsObj;
                for (Object elem : rawList) {
                    if (elem instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> item = (Map<String, Object>) elem;
                        items.add(item);
                    } else {
                        String className;
                        if (elem != null) {
                            className = elem.getClass().toString();
                        } else {
                            className = "null";
                        }
                        log.warn("Unexpected item type in 'items' list: {}", className);
                    }
                }
            } else if (itemsObj != null) {
                log.warn("'items' is not a List: {}", itemsObj.getClass());
            }

            if (!items.isEmpty()) {
                long lastCheckEpoch = lastCheck.toEpochSecond(ZoneOffset.UTC);
                for (Map<String, Object> item : items) {
                    String addedAt = (String) item.get("added_at");
                    if (addedAt != null) {
                        long addedEpoch = LocalDateTime.parse(
                                addedAt.substring(0, ISO_DATE_LENGTH))
                            .toEpochSecond(ZoneOffset.UTC);
                        if (addedEpoch > lastCheckEpoch) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> track = (Map<String, Object>) item.get("track");
                            events.add(createTrackEvent(track, "new_saved_track"));
                        }
                    }
                }
            }

            log.debug("Found {} new saved tracks", events.size());
            return events;
        } catch (Exception e) {
            log.error("Error checking new saved tracks: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Trigger: Check if playback started
     */
    private List<Map<String, Object>> checkPlaybackStarted(String token,
                                                           Map<String, Object> params,
                                                           LocalDateTime lastCheck) {
        try {
            String url = SPOTIFY_API_BASE + "/me/player";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Collections.emptyList();
            }

            Map<String, Object> body = response.getBody();
            Boolean isPlaying = (Boolean) body.get("is_playing");

            Object userIdObj = params.get("_internal_user_id");
            UUID userId = null;
            if (userIdObj instanceof UUID) {
                userId = (UUID) userIdObj;
            } else if (userIdObj instanceof String) {
                try {
                    userId = UUID.fromString((String) userIdObj);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid userId format in params: {}", userIdObj);
                }
            }

            if (userId == null) {
                if (Boolean.TRUE.equals(isPlaying)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = (Map<String, Object>) body.get("item");
                    if (item != null) {
                        return List.of(createTrackEvent(item, "playback_started"));
                    }
                }
                return Collections.emptyList();
            }

            Boolean lastPlaybackState = lastKnownPlaybackStates.get(userId);
            boolean wasNotPlaying = (lastPlaybackState == null) || !lastPlaybackState;
            boolean isNowPlaying = Boolean.TRUE.equals(isPlaying);

            lastKnownPlaybackStates.put(userId, isNowPlaying);

            if (wasNotPlaying && isNowPlaying) {
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) body.get("item");
                if (item != null) {
                    String trackId = (String) item.get("id");
                    if (trackId != null) {
                        lastKnownTrackIds.put(userId, trackId);
                    }
                    log.debug("Playback started for user {}", userId);
                    return List.of(createTrackEvent(item, "playback_started"));
                }
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error checking playback started: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Trigger: Check if current track changed
     */
    private List<Map<String, Object>> checkTrackChanged(String token,
                                                        Map<String, Object> params,
                                                        LocalDateTime lastCheck) {
        try {
            String url = SPOTIFY_API_BASE + "/me/player/currently-playing";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Collections.emptyList();
            }

            Map<String, Object> body = response.getBody();

            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) body.get("item");
            Boolean isPlaying = (Boolean) body.get("is_playing");

            if (item == null) {
                return Collections.emptyList();
            }

            String currentTrackId = (String) item.get("id");
            if (currentTrackId == null) {
                return Collections.emptyList();
            }

            Object userIdObj = params.get("_internal_user_id");
            UUID userId = null;
            if (userIdObj instanceof UUID) {
                userId = (UUID) userIdObj;
            } else if (userIdObj instanceof String) {
                try {
                    userId = UUID.fromString((String) userIdObj);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid userId format in params: {}", userIdObj);
                }
            }

            if (userId == null) {
                log.debug("No userId available for track change detection, triggering event");
                return List.of(createTrackEvent(item, "track_changed"));
            }

            String lastKnownTrackId = lastKnownTrackIds.get(userId);

            boolean trackChanged = !currentTrackId.equals(lastKnownTrackId);

            lastKnownTrackIds.put(userId, currentTrackId);
            lastKnownPlaybackStates.put(userId, Boolean.TRUE.equals(isPlaying));
            if (trackChanged && Boolean.TRUE.equals(isPlaying)) {
                log.debug("Track changed for user {}: {} -> {}", userId, lastKnownTrackId, currentTrackId);
                return List.of(createTrackEvent(item, "track_changed"));
            }

            log.trace("No track change detected for user {} (current: {}, playing: {})",
                     userId, currentTrackId, isPlaying);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error checking track changed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Trigger: Check if a playlist was updated
     */
    private List<Map<String, Object>> checkPlaylistUpdated(String token,
                                                           Map<String, Object> params,
                                                           LocalDateTime lastCheck) {
        try {
            String playlistId = (String) params.get("playlist_id");
            if (playlistId == null || playlistId.isEmpty()) {
                log.warn("No playlist_id provided for playlist_updated trigger");
                return Collections.emptyList();
            }

            String url = SPOTIFY_API_BASE + "/playlists/" + playlistId;
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Collections.emptyList();
            }

            Map<String, Object> playlist = response.getBody();
            Map<String, Object> event = new HashMap<>();
            event.put("event_type", "playlist_updated");
            event.put("playlist_id", playlist.get("id"));
            event.put("playlist_name", playlist.get("name"));
            Object tracksObj = playlist.get("tracks");
            Object tracksTotal = null;
            if (tracksObj instanceof Map) {
                Object totalObj = ((Map<?, ?>) tracksObj).get("total");
                tracksTotal = totalObj;
            }
            event.put("tracks_total", tracksTotal);
            event.put("timestamp", LocalDateTime.now().toString());

            return List.of(event);
        } catch (Exception e) {
            log.error("Error checking playlist updated: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }


    /**
     * Action: Play a specific track
     */
    private Map<String, Object> playTrack(String token,
                                         Map<String, Object> inputPayload,
                                         Map<String, Object> actionParams) {
        try {
            String trackUri = (String) actionParams.get("track_uri");
            if (trackUri == null || trackUri.isEmpty()) {
                trackUri = (String) inputPayload.get("track_uri");
            }

            if (trackUri == null || trackUri.isEmpty()) {
                throw new IllegalArgumentException("track_uri is required");
            }

            String url = SPOTIFY_API_BASE + "/me/player/play";
            HttpHeaders headers = createHeaders(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("uris", List.of(trackUri));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Track started playing");
            result.put("track_uri", trackUri);
            return result;
        } catch (Exception e) {
            log.error("Error playing track: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to play track: " + e.getMessage(), e);
        }
    }

    /**
     * Action: Pause playback
     */
    private Map<String, Object> pausePlayback(String token) {
        try {
            String url = SPOTIFY_API_BASE + "/me/player/pause";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Playback paused");
            return result;
        } catch (Exception e) {
            log.error("Error pausing playback: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to pause playback: " + e.getMessage(), e);
        }
    }

    /**
     * Action: Skip to next track
     */
    private Map<String, Object> skipToNext(String token) {
        try {
            String url = SPOTIFY_API_BASE + "/me/player/next";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Skipped to next track");
            return result;
        } catch (Exception e) {
            log.error("Error skipping to next: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to skip to next: " + e.getMessage(), e);
        }
    }

    /**
     * Action: Skip to previous track
     */
    private Map<String, Object> skipToPrevious(String token) {
        try {
            String url = SPOTIFY_API_BASE + "/me/player/previous";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Skipped to previous track");
            return result;
        } catch (Exception e) {
            log.error("Error skipping to previous: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to skip to previous: " + e.getMessage(), e);
        }
    }

    /**
     * Action: Add track to queue
     */
    private Map<String, Object> addToQueue(String token,
                                          Map<String, Object> inputPayload,
                                          Map<String, Object> actionParams) {
        try {
            String trackUri = (String) actionParams.get("track_uri");
            if (trackUri == null || trackUri.isEmpty()) {
                trackUri = (String) inputPayload.get("track_uri");
            }

            if (trackUri == null || trackUri.isEmpty()) {
                throw new IllegalArgumentException("track_uri is required");
            }

            String url = SPOTIFY_API_BASE + "/me/player/queue?uri=" + trackUri;
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Track added to queue");
            result.put("track_uri", trackUri);
            return result;
        } catch (Exception e) {
            log.error("Error adding to queue: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add to queue: " + e.getMessage(), e);
        }
    }

    /**
     * Action: Save track to library
     */
    private Map<String, Object> saveTrack(String token,
                                         Map<String, Object> inputPayload,
                                         Map<String, Object> actionParams) {
        try {
            String trackId = (String) actionParams.get("track_id");
            if (trackId == null || trackId.isEmpty()) {
                trackId = (String) inputPayload.get("track_id");
            }

            if (trackId == null || trackId.isEmpty()) {
                throw new IllegalArgumentException("track_id is required");
            }

            String url = SPOTIFY_API_BASE + "/me/tracks?ids=" + trackId;
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Track saved to library");
            result.put("track_id", trackId);
            return result;
        } catch (Exception e) {
            log.error("Error saving track: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save track: " + e.getMessage(), e);
        }
    }

    /**
     * Action: Create a new playlist
     */
    private Map<String, Object> createPlaylist(String token,
                                               Map<String, Object> inputPayload,
                                               Map<String, Object> actionParams) {
        try {
            String playlistName = (String) actionParams.get("playlist_name");
            if (playlistName == null || playlistName.isEmpty()) {
                playlistName = (String) inputPayload.get("playlist_name");
            }

            if (playlistName == null || playlistName.isEmpty()) {
                throw new IllegalArgumentException("playlist_name is required");
            }

            String userId = getCurrentUserId(token);

            String url = SPOTIFY_API_BASE + "/users/" + userId + "/playlists";
            HttpHeaders headers = createHeaders(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("name", playlistName);
            body.put("public", actionParams.getOrDefault("public", false));
            body.put("description", actionParams.getOrDefault("description", "Created by AREA"));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, entity,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Playlist created");
            result.put("playlist_id", response.getBody().get("id"));
            result.put("playlist_name", playlistName);
            return result;
        } catch (Exception e) {
            log.error("Error creating playlist: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create playlist: " + e.getMessage(), e);
        }
    }

    /**
     * Action: Add track to playlist
     */
    private Map<String, Object> addToPlaylist(String token,
                                              Map<String, Object> inputPayload,
                                              Map<String, Object> actionParams) {
        try {
            String playlistId = (String) actionParams.get("playlist_id");
            String trackUri = (String) actionParams.get("track_uri");

            if (trackUri == null || trackUri.isEmpty()) {
                trackUri = (String) inputPayload.get("track_uri");
            }

            if (playlistId == null || playlistId.isEmpty() || trackUri == null || trackUri.isEmpty()) {
                throw new IllegalArgumentException("playlist_id and track_uri are required");
            }

            String url = SPOTIFY_API_BASE + "/playlists/" + playlistId + "/tracks";
            HttpHeaders headers = createHeaders(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("uris", List.of(trackUri));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Track added to playlist");
            result.put("playlist_id", playlistId);
            result.put("track_uri", trackUri);
            return result;
        } catch (Exception e) {
            log.error("Error adding to playlist: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add to playlist: " + e.getMessage(), e);
        }
    }

    /**
     * Action: Set volume
     */
    private Map<String, Object> setVolume(String token,
                                         Map<String, Object> inputPayload,
                                         Map<String, Object> actionParams) {
        try {
            Integer volume = (Integer) actionParams.get("volume");
            if (volume == null) {
                Object volumeObj = inputPayload.get("volume");
                if (volumeObj instanceof Integer) {
                    volume = (Integer) volumeObj;
                } else if (volumeObj instanceof String) {
                    volume = Integer.parseInt((String) volumeObj);
                }
            }

            if (volume == null || volume < 0 || volume > MAX_VOLUME) {
                throw new IllegalArgumentException("volume must be between 0 and 100");
            }

            String url = SPOTIFY_API_BASE + "/me/player/volume?volume_percent=" + volume;
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Volume set to " + volume);
            result.put("volume", volume);
            return result;
        } catch (Exception e) {
            log.error("Error setting volume: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to set volume: " + e.getMessage(), e);
        }
    }

    /**
     * Action: Set repeat mode
     */
    private Map<String, Object> setRepeatMode(String token,
                                              Map<String, Object> inputPayload,
                                              Map<String, Object> actionParams) {
        try {
            String state = (String) actionParams.get("state");
            if (state == null || state.isEmpty()) {
                state = (String) inputPayload.get("state");
            }

            if (state == null || (!state.equals("track") && !state.equals("context") && !state.equals("off"))) {
                throw new IllegalArgumentException("state must be 'track', 'context', or 'off'");
            }

            String url = SPOTIFY_API_BASE + "/me/player/repeat?state=" + state;
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Repeat mode set to " + state);
            result.put("state", state);
            return result;
        } catch (Exception e) {
            log.error("Error setting repeat mode: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to set repeat mode: " + e.getMessage(), e);
        }
    }


    private String getSpotifyToken(UUID userId) {
        try {
            Optional<UserOAuthIdentity> oauthIdentity = userOAuthIdentityRepository
                .findByUserIdAndProvider(userId, SPOTIFY_PROVIDER_KEY);

            if (oauthIdentity.isEmpty()) {
                log.warn("No Spotify OAuth identity found for user: {}", userId);
                return null;
            }

            UserOAuthIdentity identity = oauthIdentity.get();

            if (oauthTokenRefreshService.needsRefresh(identity)) {
                log.info("Spotify access token expired or about to expire for user {}, refreshing...", userId);
                boolean refreshed = oauthTokenRefreshService.refreshSpotifyToken(
                    identity, spotifyClientId, spotifyClientSecret);
                if (!refreshed) {
                    log.error("Failed to refresh Spotify token for user {}", userId);
                    return null;
                }
                identity = userOAuthIdentityRepository
                    .findByUserIdAndProvider(userId, SPOTIFY_PROVIDER_KEY)
                    .orElse(null);
                if (identity == null) {
                    log.error("Failed to reload Spotify identity after refresh for user {}", userId);
                    return null;
                }
            }

            String encryptedToken = identity.getAccessTokenEnc();
            if (encryptedToken == null) {
                log.warn("No Spotify access token for user: {}", userId);
                return null;
            }

            return tokenEncryptionService.decryptToken(encryptedToken);
        } catch (Exception e) {
            log.error("Error getting Spotify token for user {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    private String getCurrentUserId(String token) {
        try {
            String url = SPOTIFY_API_BASE + "/me";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (response.getBody() != null) {
                return (String) response.getBody().get("id");
            }
            throw new RuntimeException("Failed to get current user ID");
        } catch (Exception e) {
            log.error("Error getting current user ID: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get current user ID: " + e.getMessage(), e);
        }
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> createTrackEvent(Map<String, Object> track, String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("event_type", eventType);
        event.put("track_id", track.get("id"));
        event.put("track_name", track.get("name"));
        event.put("track_uri", track.get("uri"));

        Object artistsObj = track.get("artists");
        List<Map<String, Object>> artists = new ArrayList<>();
        if (artistsObj instanceof List<?>) {
            List<?> rawList = (List<?>) artistsObj;
            for (Object item : rawList) {
                if (item instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> artistMap = (Map<String, Object>) item;
                    artists.add(artistMap);
                }
            }
        }
        if (!artists.isEmpty()) {
            event.put("artist_name", artists.get(0).get("name"));
        }

        Object albumObj = track.get("album");
        if (albumObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> album = (Map<String, Object>) albumObj;
            event.put("album_name", album.get("name"));
        }

        event.put("timestamp", LocalDateTime.now().toString());
        return event;
    }
}
