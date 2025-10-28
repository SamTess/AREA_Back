-- ============================================
-- AREA - Discord Service and Actions
-- Add Discord service and basic action/reaction definitions
-- ============================================

-- Add Discord service
INSERT INTO area.a_services (key, name, auth, is_active, docs_url, icon_light_url, icon_dark_url)
VALUES ('discord', 'Discord', 'OAUTH2', true, 'https://discord.com/developers/docs/intro',
        'https://img.icons8.com/color/96/discord-logo.png',
        'https://img.icons8.com/color/96/discord-logo.png')
ON CONFLICT (key) DO UPDATE SET
    name = EXCLUDED.name,
    auth = EXCLUDED.auth,
    is_active = EXCLUDED.is_active,
    docs_url = EXCLUDED.docs_url,
    icon_light_url = EXCLUDED.icon_light_url,
    icon_dark_url = EXCLUDED.icon_dark_url;

-- Get Discord service ID for references
WITH discord_service AS (
    SELECT id FROM area.a_services WHERE key = 'discord'
)

-- Insert Discord Actions (Event-capable) and Reactions (Executable)
INSERT INTO area.a_action_definitions (
    service_id, key, name, description,
    is_event_capable, is_executable, version,
    input_schema, output_schema,
    default_poll_interval_seconds
)

-- Discord: Message Created (event)
SELECT
    ds.id,
    'message_created',
    'Message Created',
    'Triggered when a new message is created in a channel the bot can access',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "guild_id": { "type": "string", "description": "Guild (server) id (optional)" },
            "channel_id": { "type": "string", "description": "Channel id to monitor (optional)" },
            "contains_text": { "type": "string", "description": "Filter messages that contain this text (optional)" }
        },
        "required": []
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "message_id": { "type": "string" },
            "channel_id": { "type": "string" },
            "guild_id": { "type": "string" },
            "author_id": { "type": "string" },
            "content": { "type": "string" },
            "created_at": { "type": "string", "format": "date-time" }
        }
    }'::jsonb,
    30
FROM discord_service ds

UNION ALL

-- Discord: Message Reaction Added (event)
SELECT
    ds.id,
    'reaction_added',
    'Reaction Added',
    'Triggered when a reaction is added to a message',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "guild_id": { "type": "string", "description": "Guild (server) id (optional)" },
            "channel_id": { "type": "string", "description": "Channel id to monitor (optional)" },
            "emoji": { "type": "string", "description": "Emoji unicode or id to filter (optional)" }
        },
        "required": []
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "message_id": { "type": "string" },
            "channel_id": { "type": "string" },
            "guild_id": { "type": "string" },
            "user_id": { "type": "string" },
            "emoji": { "type": "string" },
            "created_at": { "type": "string", "format": "date-time" }
        }
    }'::jsonb,
    30
FROM discord_service ds

UNION ALL

-- Discord Reaction: Send Message (executable)
SELECT
    ds.id,
    'send_message',
    'Send Message',
    'Send a message to a channel as the bot',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "channel_id": { "type": "string", "description": "Channel id to post the message" },
            "content": { "type": "string", "description": "Message content" },
            "tts": { "type": "boolean", "description": "Text-to-speech" }
        },
        "required": ["channel_id", "content"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "message_id": { "type": "string" },
            "channel_id": { "type": "string" },
            "created_at": { "type": "string", "format": "date-time" }
        }
    }'::jsonb,
    null
FROM discord_service ds

UNION ALL

-- Discord Reaction: Add Role to User (executable)
SELECT
    ds.id,
    'add_role',
    'Add Role',
    'Assign a role to a member in a guild',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "guild_id": { "type": "string", "description": "Guild id where to add the role" },
            "user_id": { "type": "string", "description": "User id to modify" },
            "role_id": { "type": "string", "description": "Role id to assign" }
        },
        "required": ["guild_id", "user_id", "role_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "guild_id": { "type": "string" },
            "user_id": { "type": "string" },
            "role_id": { "type": "string" },
            "updated_at": { "type": "string", "format": "date-time" }
        }
    }'::jsonb,
    null
FROM discord_service ds

ON CONFLICT (service_id, key, version) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_event_capable = EXCLUDED.is_event_capable,
    is_executable = EXCLUDED.is_executable,
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    default_poll_interval_seconds = EXCLUDED.default_poll_interval_seconds,
    updated_at = now();
