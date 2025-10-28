package area.server.AREA_Back.service;

import area.server.AREA_Back.entity.UserOAuthIdentity;
import area.server.AREA_Back.repository.UserOAuthIdentityRepository;
import area.server.AREA_Back.service.Area.Services.SpotifyActionService;
import area.server.AREA_Back.service.Auth.OAuthTokenRefreshService;
import area.server.AREA_Back.service.Auth.TokenEncryptionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SpotifyActionService
 * Tests Spotify API interactions for playback control, playlist management, and event checking
 */
@ExtendWith(MockitoExtension.class)
class SpotifyActionServiceTest {

    @Mock
    private UserOAuthIdentityRepository userOAuthIdentityRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OAuthTokenRefreshService oauthTokenRefreshService;

    private SimpleMeterRegistry meterRegistry;
    private SpotifyActionService spotifyActionService;

    private static final String TEST_TOKEN = "test-spotify-token";
    private static final String TEST_ENCRYPTED_TOKEN = "encrypted-token";
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final String SPOTIFY_PROVIDER = "spotify";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        spotifyActionService = new SpotifyActionService(
            userOAuthIdentityRepository,
            tokenEncryptionService,
            restTemplate,
            meterRegistry,
            oauthTokenRefreshService
        );

        // Manually initialize metrics
        try {
            var initMethod = SpotifyActionService.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(spotifyActionService);
        } catch (Exception e) {
            fail("Failed to initialize metrics: " + e.getMessage());
        }
    }

    @Test
    void testServiceInitialization() {
        assertNotNull(spotifyActionService);
        assertNotNull(meterRegistry);
    }

    @Test
    void testMetricsAreRegistered() {
        assertNotNull(meterRegistry.find("spotify_actions_executed_total").counter());
        assertNotNull(meterRegistry.find("spotify_actions_failed_total").counter());
        assertNotNull(meterRegistry.find("spotify_events_checked_total").counter());
    }

    // ==================== executeSpotifyAction Tests ====================

    @Test
    void testExecuteSpotifyActionWithoutToken() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, SPOTIFY_PROVIDER))
            .thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            spotifyActionService.executeSpotifyAction("play_track", inputPayload, actionParams, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("No Spotify token found"));
    }

    @Test
    void testExecuteSpotifyActionWithUnknownAction() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        setupMockToken();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            spotifyActionService.executeSpotifyAction("unknown_action", inputPayload, actionParams, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("Unknown Spotify action"));
    }

    @Test
    void testExecuteSpotifyActionIncrementsMetrics() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, SPOTIFY_PROVIDER))
            .thenReturn(Optional.empty());

        double initialExecuted = meterRegistry.counter("spotify_actions_executed_total").count();
        double initialFailed = meterRegistry.counter("spotify_actions_failed_total").count();

        // When
        try {
            spotifyActionService.executeSpotifyAction("play_track", inputPayload, actionParams, TEST_USER_ID);
        } catch (Exception e) {
            // Expected to fail
        }

        // Then
        double finalExecuted = meterRegistry.counter("spotify_actions_executed_total").count();
        double finalFailed = meterRegistry.counter("spotify_actions_failed_total").count();

        assertEquals(initialExecuted + 1, finalExecuted);
        assertEquals(initialFailed + 1, finalFailed);
    }

    @Test
    void testExecuteSpotifyActionPlayTrack() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("track_uri", "spotify:track:123");

        setupMockToken();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        // When
        Map<String, Object> result = spotifyActionService.executeSpotifyAction(
            "play_track", inputPayload, actionParams, TEST_USER_ID
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("Track started playing", result.get("message"));
        assertEquals("spotify:track:123", result.get("track_uri"));
    }

    @Test
    void testExecuteSpotifyActionPausePlayback() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        setupMockToken();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        // When
        Map<String, Object> result = spotifyActionService.executeSpotifyAction(
            "pause_playback", inputPayload, actionParams, TEST_USER_ID
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("Playback paused", result.get("message"));
    }

    @Test
    void testExecuteSpotifyActionSkipToNext() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        setupMockToken();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        // When
        Map<String, Object> result = spotifyActionService.executeSpotifyAction(
            "skip_to_next", inputPayload, actionParams, TEST_USER_ID
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("Skipped to next track", result.get("message"));
    }

    @Test
    void testExecuteSpotifyActionSkipToPrevious() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        setupMockToken();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        // When
        Map<String, Object> result = spotifyActionService.executeSpotifyAction(
            "skip_to_previous", inputPayload, actionParams, TEST_USER_ID
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("Skipped to previous track", result.get("message"));
    }

    @Test
    void testExecuteSpotifyActionAddToQueue() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("track_uri", "spotify:track:456");

        setupMockToken();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        // When
        Map<String, Object> result = spotifyActionService.executeSpotifyAction(
            "add_to_queue", inputPayload, actionParams, TEST_USER_ID
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("Track added to queue", result.get("message"));
        assertEquals("spotify:track:456", result.get("track_uri"));
    }

    @Test
    void testExecuteSpotifyActionSaveTrack() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("track_id", "track123");

        setupMockToken();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        // When
        Map<String, Object> result = spotifyActionService.executeSpotifyAction(
            "save_track", inputPayload, actionParams, TEST_USER_ID
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("Track saved to library", result.get("message"));
        assertEquals("track123", result.get("track_id"));
    }

    @Test
    void testExecuteSpotifyActionSetVolume() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("volume", 75);

        setupMockToken();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        // When
        Map<String, Object> result = spotifyActionService.executeSpotifyAction(
            "set_volume", inputPayload, actionParams, TEST_USER_ID
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("Volume set to 75", result.get("message"));
        assertEquals(75, result.get("volume"));
    }

    @Test
    void testExecuteSpotifyActionSetVolumeFromInputPayload() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        inputPayload.put("volume", 50);
        Map<String, Object> actionParams = new HashMap<>();

        setupMockToken();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        // When
        Map<String, Object> result = spotifyActionService.executeSpotifyAction(
            "set_volume", inputPayload, actionParams, TEST_USER_ID
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals(50, result.get("volume"));
    }

    @Test
    void testExecuteSpotifyActionSetVolumeInvalidRange() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("volume", 150);

        setupMockToken();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            spotifyActionService.executeSpotifyAction("set_volume", inputPayload, actionParams, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("volume must be between 0 and 100"));
    }

    @Test
    void testExecuteSpotifyActionSetRepeatMode() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("state", "track");

        setupMockToken();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        // When
        Map<String, Object> result = spotifyActionService.executeSpotifyAction(
            "set_repeat_mode", inputPayload, actionParams, TEST_USER_ID
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("Repeat mode set to track", result.get("message"));
        assertEquals("track", result.get("state"));
    }

    @Test
    void testExecuteSpotifyActionSetRepeatModeInvalidState() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("state", "invalid");

        setupMockToken();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            spotifyActionService.executeSpotifyAction("set_repeat_mode", inputPayload, actionParams, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("state must be"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecuteSpotifyActionCreatePlaylist() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("playlist_name", "My Test Playlist");

        setupMockToken();

        // Mock getCurrentUserId call
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", "spotify_user_123");
        when(restTemplate.exchange(
            contains("/me"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userResponse, HttpStatus.OK));

        // Mock createPlaylist call
        Map<String, Object> playlistResponse = new HashMap<>();
        playlistResponse.put("id", "playlist_123");
        playlistResponse.put("name", "My Test Playlist");
        when(restTemplate.exchange(
            contains("/playlists"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(playlistResponse, HttpStatus.OK));

        // When
        Map<String, Object> result = spotifyActionService.executeSpotifyAction(
            "create_playlist", inputPayload, actionParams, TEST_USER_ID
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("Playlist created", result.get("message"));
        assertEquals("playlist_123", result.get("playlist_id"));
        assertEquals("My Test Playlist", result.get("playlist_name"));
    }

    @Test
    void testExecuteSpotifyActionAddToPlaylist() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("playlist_id", "playlist_123");
        actionParams.put("track_uri", "spotify:track:789");

        setupMockToken();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        // When
        Map<String, Object> result = spotifyActionService.executeSpotifyAction(
            "add_to_playlist", inputPayload, actionParams, TEST_USER_ID
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("Track added to playlist", result.get("message"));
        assertEquals("playlist_123", result.get("playlist_id"));
        assertEquals("spotify:track:789", result.get("track_uri"));
    }

    // ==================== checkSpotifyEvents Tests ====================

    @Test
    void testCheckSpotifyEventsWithoutToken() {
        // Given
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, SPOTIFY_PROVIDER))
            .thenReturn(Optional.empty());

        // When
        List<Map<String, Object>> events = spotifyActionService.checkSpotifyEvents(
            "new_saved_track", actionParams, TEST_USER_ID, lastCheck
        );

        // Then
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void testCheckSpotifyEventsWithUnknownAction() {
        // Given
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        setupMockToken();

        // When
        List<Map<String, Object>> events = spotifyActionService.checkSpotifyEvents(
            "unknown_event", actionParams, TEST_USER_ID, lastCheck
        );

        // Then
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void testCheckSpotifyEventsIncrementsMetrics() {
        // Given
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        setupMockToken();

        double initialChecked = meterRegistry.counter("spotify_events_checked_total").count();

        // When
        spotifyActionService.checkSpotifyEvents("unknown_event", actionParams, TEST_USER_ID, lastCheck);

        // Then
        double finalChecked = meterRegistry.counter("spotify_events_checked_total").count();
        assertEquals(initialChecked + 1, finalChecked);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckNewSavedTracks() {
        // Given
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusHours(1);

        setupMockToken();

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        
        Map<String, Object> item1 = new HashMap<>();
        item1.put("added_at", LocalDateTime.now().toString());
        Map<String, Object> track1 = new HashMap<>();
        track1.put("id", "track1");
        track1.put("name", "Track 1");
        track1.put("uri", "spotify:track:1");
        List<Map<String, Object>> artists = new ArrayList<>();
        Map<String, Object> artist = new HashMap<>();
        artist.put("name", "Artist 1");
        artists.add(artist);
        track1.put("artists", artists);
        Map<String, Object> album = new HashMap<>();
        album.put("name", "Album 1");
        track1.put("album", album);
        item1.put("track", track1);
        items.add(item1);

        response.put("items", items);

        when(restTemplate.exchange(
            contains("/me/tracks"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        // When
        List<Map<String, Object>> events = spotifyActionService.checkSpotifyEvents(
            "new_saved_track", actionParams, TEST_USER_ID, lastCheck
        );

        // Then
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("new_saved_track", events.get(0).get("event_type"));
        assertEquals("track1", events.get(0).get("track_id"));
        assertEquals("Track 1", events.get(0).get("track_name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckNewSavedTracksNoNewTracks() {
        // Given
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().plusHours(1); // Future time

        setupMockToken();

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        
        Map<String, Object> item1 = new HashMap<>();
        item1.put("added_at", LocalDateTime.now().minusHours(2).toString());
        Map<String, Object> track1 = new HashMap<>();
        track1.put("id", "track1");
        item1.put("track", track1);
        items.add(item1);

        response.put("items", items);

        when(restTemplate.exchange(
            contains("/me/tracks"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        // When
        List<Map<String, Object>> events = spotifyActionService.checkSpotifyEvents(
            "new_saved_track", actionParams, TEST_USER_ID, lastCheck
        );

        // Then
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckPlaybackStarted() {
        // Given
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        setupMockToken();

        Map<String, Object> response = new HashMap<>();
        response.put("is_playing", true);
        Map<String, Object> item = new HashMap<>();
        item.put("id", "track123");
        item.put("name", "Current Track");
        item.put("uri", "spotify:track:123");
        response.put("item", item);

        when(restTemplate.exchange(
            contains("/me/player"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        // When
        List<Map<String, Object>> events = spotifyActionService.checkSpotifyEvents(
            "playback_started", actionParams, TEST_USER_ID, lastCheck
        );

        // Then
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("playback_started", events.get(0).get("event_type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckPlaybackStartedNotPlaying() {
        // Given
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        setupMockToken();

        Map<String, Object> response = new HashMap<>();
        response.put("is_playing", false);

        when(restTemplate.exchange(
            contains("/me/player"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        // When
        List<Map<String, Object>> events = spotifyActionService.checkSpotifyEvents(
            "playback_started", actionParams, TEST_USER_ID, lastCheck
        );

        // Then
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckTrackChanged() {
        // Given
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        setupMockToken();

        Map<String, Object> response = new HashMap<>();
        Map<String, Object> item = new HashMap<>();
        item.put("id", "track456");
        item.put("name", "Changed Track");
        item.put("uri", "spotify:track:456");
        response.put("item", item);

        when(restTemplate.exchange(
            contains("/currently-playing"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        // When
        List<Map<String, Object>> events = spotifyActionService.checkSpotifyEvents(
            "track_changed", actionParams, TEST_USER_ID, lastCheck
        );

        // Then
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("track_changed", events.get(0).get("event_type"));
        assertEquals("track456", events.get(0).get("track_id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckPlaylistUpdated() {
        // Given
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("playlist_id", "playlist123");
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        setupMockToken();

        Map<String, Object> response = new HashMap<>();
        response.put("id", "playlist123");
        response.put("name", "My Playlist");
        Map<String, Object> tracks = new HashMap<>();
        tracks.put("total", 42);
        response.put("tracks", tracks);

        when(restTemplate.exchange(
            contains("/playlists/playlist123"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        // When
        List<Map<String, Object>> events = spotifyActionService.checkSpotifyEvents(
            "playlist_updated", actionParams, TEST_USER_ID, lastCheck
        );

        // Then
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("playlist_updated", events.get(0).get("event_type"));
        assertEquals("playlist123", events.get(0).get("playlist_id"));
        assertEquals("My Playlist", events.get(0).get("playlist_name"));
        assertEquals(42, events.get(0).get("tracks_total"));
    }

    @Test
    void testCheckPlaylistUpdatedMissingPlaylistId() {
        // Given
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        setupMockToken();

        // When
        List<Map<String, Object>> events = spotifyActionService.checkSpotifyEvents(
            "playlist_updated", actionParams, TEST_USER_ID, lastCheck
        );

        // Then
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    // ==================== getSpotifyToken Tests ====================

    @Test
    void testGetSpotifyTokenSuccess() throws Exception {
        // Given
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc(TEST_ENCRYPTED_TOKEN);
        identity.setProvider(SPOTIFY_PROVIDER);

        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, SPOTIFY_PROVIDER))
            .thenReturn(Optional.of(identity));
        when(oauthTokenRefreshService.needsRefresh(identity)).thenReturn(false);
        when(tokenEncryptionService.decryptToken(TEST_ENCRYPTED_TOKEN)).thenReturn(TEST_TOKEN);

        // Use reflection to call private method
        var method = SpotifyActionService.class.getDeclaredMethod("getSpotifyToken", UUID.class);
        method.setAccessible(true);
        String token = (String) method.invoke(spotifyActionService, TEST_USER_ID);

        // Then
        assertEquals(TEST_TOKEN, token);
    }

    @Test
    void testGetSpotifyTokenWithRefresh() throws Exception {
        // Given
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc(TEST_ENCRYPTED_TOKEN);
        identity.setProvider(SPOTIFY_PROVIDER);

        UserOAuthIdentity refreshedIdentity = new UserOAuthIdentity();
        refreshedIdentity.setAccessTokenEnc("new-encrypted-token");
        refreshedIdentity.setProvider(SPOTIFY_PROVIDER);

        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, SPOTIFY_PROVIDER))
            .thenReturn(Optional.of(identity))
            .thenReturn(Optional.of(refreshedIdentity));
        when(oauthTokenRefreshService.needsRefresh(identity)).thenReturn(true);
        when(oauthTokenRefreshService.refreshSpotifyToken(any(), any(), any())).thenReturn(true);
        when(tokenEncryptionService.decryptToken("new-encrypted-token")).thenReturn("new-token");

        // Use reflection to call private method
        var method = SpotifyActionService.class.getDeclaredMethod("getSpotifyToken", UUID.class);
        method.setAccessible(true);
        String token = (String) method.invoke(spotifyActionService, TEST_USER_ID);

        // Then
        assertEquals("new-token", token);
    }

    @Test
    void testGetSpotifyTokenRefreshFailed() throws Exception {
        // Given
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc(TEST_ENCRYPTED_TOKEN);
        identity.setProvider(SPOTIFY_PROVIDER);

        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, SPOTIFY_PROVIDER))
            .thenReturn(Optional.of(identity));
        when(oauthTokenRefreshService.needsRefresh(identity)).thenReturn(true);
        when(oauthTokenRefreshService.refreshSpotifyToken(any(), any(), any())).thenReturn(false);

        // Use reflection to call private method
        var method = SpotifyActionService.class.getDeclaredMethod("getSpotifyToken", UUID.class);
        method.setAccessible(true);
        String token = (String) method.invoke(spotifyActionService, TEST_USER_ID);

        // Then
        assertNull(token);
    }

    // ==================== createTrackEvent Tests ====================

    @Test
    void testCreateTrackEvent() throws Exception {
        // Given
        Map<String, Object> track = new HashMap<>();
        track.put("id", "track123");
        track.put("name", "Test Track");
        track.put("uri", "spotify:track:123");

        List<Map<String, Object>> artists = new ArrayList<>();
        Map<String, Object> artist = new HashMap<>();
        artist.put("name", "Test Artist");
        artists.add(artist);
        track.put("artists", artists);

        Map<String, Object> album = new HashMap<>();
        album.put("name", "Test Album");
        track.put("album", album);

        // Use reflection to call private method
        var method = SpotifyActionService.class.getDeclaredMethod(
            "createTrackEvent", Map.class, String.class
        );
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) method.invoke(
            spotifyActionService, track, "test_event"
        );

        // Then
        assertNotNull(event);
        assertEquals("test_event", event.get("event_type"));
        assertEquals("track123", event.get("track_id"));
        assertEquals("Test Track", event.get("track_name"));
        assertEquals("spotify:track:123", event.get("track_uri"));
        assertEquals("Test Artist", event.get("artist_name"));
        assertEquals("Test Album", event.get("album_name"));
        assertNotNull(event.get("timestamp"));
    }

    @Test
    void testCreateTrackEventWithoutArtistsAndAlbum() throws Exception {
        // Given
        Map<String, Object> track = new HashMap<>();
        track.put("id", "track123");
        track.put("name", "Test Track");
        track.put("uri", "spotify:track:123");

        // Use reflection to call private method
        var method = SpotifyActionService.class.getDeclaredMethod(
            "createTrackEvent", Map.class, String.class
        );
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) method.invoke(
            spotifyActionService, track, "test_event"
        );

        // Then
        assertNotNull(event);
        assertEquals("test_event", event.get("event_type"));
        assertEquals("track123", event.get("track_id"));
        assertFalse(event.containsKey("artist_name"));
        assertFalse(event.containsKey("album_name"));
    }

    // ==================== getCurrentUserId Tests ====================

    @Test
    @SuppressWarnings("unchecked")
    void testGetCurrentUserId() throws Exception {
        // Given
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", "spotify_user_123");

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(userResponse, HttpStatus.OK));

        // Use reflection to call private method
        var method = SpotifyActionService.class.getDeclaredMethod("getCurrentUserId", String.class);
        method.setAccessible(true);
        String userId = (String) method.invoke(spotifyActionService, TEST_TOKEN);

        // Then
        assertEquals("spotify_user_123", userId);
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testPlayTrackMissingTrackUri() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        setupMockToken();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            spotifyActionService.executeSpotifyAction("play_track", inputPayload, actionParams, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("track_uri is required"));
    }

    @Test
    void testAddToQueueMissingTrackUri() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        setupMockToken();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            spotifyActionService.executeSpotifyAction("add_to_queue", inputPayload, actionParams, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("track_uri is required"));
    }

    @Test
    void testSaveTrackMissingTrackId() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        setupMockToken();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            spotifyActionService.executeSpotifyAction("save_track", inputPayload, actionParams, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("track_id is required"));
    }

    @Test
    void testCreatePlaylistMissingPlaylistName() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        setupMockToken();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            spotifyActionService.executeSpotifyAction("create_playlist", inputPayload, actionParams, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("playlist_name is required"));
    }

    @Test
    void testAddToPlaylistMissingParameters() {
        // Given
        Map<String, Object> inputPayload = new HashMap<>();
        Map<String, Object> actionParams = new HashMap<>();

        setupMockToken();

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            spotifyActionService.executeSpotifyAction("add_to_playlist", inputPayload, actionParams, TEST_USER_ID);
        });

        assertTrue(exception.getMessage().contains("playlist_id and track_uri are required"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCheckSpotifyEventsHandlesException() {
        // Given
        Map<String, Object> actionParams = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(5);

        setupMockToken();

        when(restTemplate.exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("API error"));

        // When
        List<Map<String, Object>> events = spotifyActionService.checkSpotifyEvents(
            "new_saved_track", actionParams, TEST_USER_ID, lastCheck
        );

        // Then
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    // ==================== Helper Methods ====================

    private void setupMockToken() {
        UserOAuthIdentity identity = new UserOAuthIdentity();
        identity.setAccessTokenEnc(TEST_ENCRYPTED_TOKEN);
        identity.setProvider(SPOTIFY_PROVIDER);

        when(userOAuthIdentityRepository.findByUserIdAndProvider(TEST_USER_ID, SPOTIFY_PROVIDER))
            .thenReturn(Optional.of(identity));
        when(oauthTokenRefreshService.needsRefresh(identity)).thenReturn(false);
        when(tokenEncryptionService.decryptToken(TEST_ENCRYPTED_TOKEN)).thenReturn(TEST_TOKEN);
    }
}
