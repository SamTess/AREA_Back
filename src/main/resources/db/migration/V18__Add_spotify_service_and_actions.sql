-- ============================================
-- AREA - Spotify Service and Actions
-- Add Spotify service and action/reaction definitions
-- ============================================

-- Add Spotify service
INSERT INTO area.a_services (key, name, auth, is_active, docs_url, icon_light_url, icon_dark_url)
VALUES ('spotify', 'Spotify', 'OAUTH2', true, 'https://developer.spotify.com/documentation/web-api',
        'https://cdn.simpleicons.org/spotify/1DB954',
        'https://cdn.simpleicons.org/spotify/1DB954')
ON CONFLICT (key) DO UPDATE SET
    name = EXCLUDED.name,
    auth = EXCLUDED.auth,
    is_active = EXCLUDED.is_active,
    docs_url = EXCLUDED.docs_url,
    icon_light_url = EXCLUDED.icon_light_url,
    icon_dark_url = EXCLUDED.icon_dark_url;

-- Get Spotify service ID for references
WITH spotify_service AS (
    SELECT id FROM area.a_services WHERE key = 'spotify'
)

-- Insert Spotify Actions (Event-capable) and Reactions (Executable)
INSERT INTO area.a_action_definitions (
    service_id, key, name, description,
    is_event_capable, is_executable, version,
    input_schema, output_schema,
    default_poll_interval_seconds
)

-- Spotify: New Saved Track (event)
SELECT
    ss.id,
    'new_saved_track',
    'New Saved Track',
    'Triggered when you save a new track to your library',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {}
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "event_type": { "type": "string" },
            "track_id": { "type": "string" },
            "track_name": { "type": "string" },
            "track_uri": { "type": "string" },
            "artist_name": { "type": "string" },
            "album_name": { "type": "string" },
            "timestamp": { "type": "string" }
        }
    }'::jsonb,
    30
FROM spotify_service ss

UNION ALL

-- Spotify: Playback Started (event)
SELECT
    ss.id,
    'playback_started',
    'Playback Started',
    'Triggered when playback starts',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {}
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "event_type": { "type": "string" },
            "track_id": { "type": "string" },
            "track_name": { "type": "string" },
            "track_uri": { "type": "string" },
            "artist_name": { "type": "string" },
            "album_name": { "type": "string" },
            "timestamp": { "type": "string" }
        }
    }'::jsonb,
    10
FROM spotify_service ss

UNION ALL

-- Spotify: Track Changed (event)
SELECT
    ss.id,
    'track_changed',
    'Track Changed',
    'Triggered when the currently playing track changes',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {}
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "event_type": { "type": "string" },
            "track_id": { "type": "string" },
            "track_name": { "type": "string" },
            "track_uri": { "type": "string" },
            "artist_name": { "type": "string" },
            "album_name": { "type": "string" },
            "timestamp": { "type": "string" }
        }
    }'::jsonb,
    5
FROM spotify_service ss

UNION ALL

-- Spotify: Playlist Updated (event)
SELECT
    ss.id,
    'playlist_updated',
    'Playlist Updated',
    'Triggered when a playlist is updated',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "playlist_id": { "type": "string", "description": "Playlist ID to monitor" }
        },
        "required": ["playlist_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "event_type": { "type": "string" },
            "playlist_id": { "type": "string" },
            "playlist_name": { "type": "string" },
            "tracks_total": { "type": "number" },
            "timestamp": { "type": "string" }
        }
    }'::jsonb,
    60
FROM spotify_service ss

UNION ALL

-- Spotify Reaction: Play Track (executable)
SELECT
    ss.id,
    'play_track',
    'Play Track',
    'Play a specific track',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "track_uri": { "type": "string", "description": "Spotify track URI (e.g., spotify:track:...)" }
        },
        "required": ["track_uri"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "status": { "type": "string" },
            "message": { "type": "string" },
            "track_uri": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM spotify_service ss

UNION ALL

-- Spotify Reaction: Pause Playback (executable)
SELECT
    ss.id,
    'pause_playback',
    'Pause Playback',
    'Pause current playback',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {}
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "status": { "type": "string" },
            "message": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM spotify_service ss

UNION ALL

-- Spotify Reaction: Skip to Next (executable)
SELECT
    ss.id,
    'skip_to_next',
    'Skip to Next',
    'Skip to next track',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {}
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "status": { "type": "string" },
            "message": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM spotify_service ss

UNION ALL

-- Spotify Reaction: Skip to Previous (executable)
SELECT
    ss.id,
    'skip_to_previous',
    'Skip to Previous',
    'Skip to previous track',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {}
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "status": { "type": "string" },
            "message": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM spotify_service ss

UNION ALL

-- Spotify Reaction: Add to Queue (executable)
SELECT
    ss.id,
    'add_to_queue',
    'Add to Queue',
    'Add a track to the playback queue',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "track_uri": { "type": "string", "description": "Spotify track URI" }
        },
        "required": ["track_uri"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "status": { "type": "string" },
            "message": { "type": "string" },
            "track_uri": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM spotify_service ss

UNION ALL

-- Spotify Reaction: Save Track (executable)
SELECT
    ss.id,
    'save_track',
    'Save Track',
    'Save a track to your library',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "track_id": { "type": "string", "description": "Spotify track ID" }
        },
        "required": ["track_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "status": { "type": "string" },
            "message": { "type": "string" },
            "track_id": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM spotify_service ss

UNION ALL

-- Spotify Reaction: Create Playlist (executable)
SELECT
    ss.id,
    'create_playlist',
    'Create Playlist',
    'Create a new playlist',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "playlist_name": { "type": "string", "description": "Playlist name" },
            "public": { "type": "boolean", "description": "Make playlist public" },
            "description": { "type": "string", "description": "Playlist description" }
        },
        "required": ["playlist_name"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "status": { "type": "string" },
            "message": { "type": "string" },
            "playlist_id": { "type": "string" },
            "playlist_name": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM spotify_service ss

UNION ALL

-- Spotify Reaction: Add to Playlist (executable)
SELECT
    ss.id,
    'add_to_playlist',
    'Add to Playlist',
    'Add a track to a playlist',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "playlist_id": { "type": "string", "description": "Playlist ID" },
            "track_uri": { "type": "string", "description": "Spotify track URI" }
        },
        "required": ["playlist_id", "track_uri"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "status": { "type": "string" },
            "message": { "type": "string" },
            "playlist_id": { "type": "string" },
            "track_uri": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM spotify_service ss

UNION ALL

-- Spotify Reaction: Set Volume (executable)
SELECT
    ss.id,
    'set_volume',
    'Set Volume',
    'Set playback volume (0-100)',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "volume": { "type": "integer", "description": "Volume percentage (0-100)", "minimum": 0, "maximum": 100 }
        },
        "required": ["volume"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "status": { "type": "string" },
            "message": { "type": "string" },
            "volume": { "type": "integer" }
        }
    }'::jsonb,
    NULL
FROM spotify_service ss

UNION ALL

-- Spotify Reaction: Set Repeat Mode (executable)
SELECT
    ss.id,
    'set_repeat_mode',
    'Set Repeat Mode',
    'Set repeat mode (track, context, or off)',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "state": { "type": "string", "description": "Repeat state: track, context, or off", "enum": ["track", "context", "off"] }
        },
        "required": ["state"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "status": { "type": "string" },
            "message": { "type": "string" },
            "state": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM spotify_service ss

-- Update existing actions if they already exist
ON CONFLICT (service_id, key, version) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_event_capable = EXCLUDED.is_event_capable,
    is_executable = EXCLUDED.is_executable,
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    default_poll_interval_seconds = EXCLUDED.default_poll_interval_seconds;
