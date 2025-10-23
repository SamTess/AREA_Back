-- ============================================
-- AREA - Discord Additional Actions
-- Add 5 new useful Discord reaction actions
-- ============================================

WITH discord_service AS (
    SELECT id FROM area.a_services WHERE key = 'discord'
)

INSERT INTO area.a_action_definitions (
    service_id, key, name, description,
    is_event_capable, is_executable, version,
    input_schema, output_schema,
    default_poll_interval_seconds
)

-- Discord Reaction: Remove Role from User (executable)
SELECT
    ds.id,
    'remove_role',
    'Remove Role',
    'Remove a role from a member in a guild',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "guild_id": { "type": "string", "description": "Guild id where to remove the role" },
            "user_id": { "type": "string", "description": "User id to modify" },
            "role_id": { "type": "string", "description": "Role id to remove" }
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
    NULL::integer
FROM discord_service ds

UNION ALL

-- Discord Reaction: Delete Message (executable)
SELECT
    ds.id,
    'delete_message',
    'Delete Message',
    'Delete a message from a channel',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "channel_id": { "type": "string", "description": "Channel id containing the message" },
            "message_id": { "type": "string", "description": "Message id to delete" }
        },
        "required": ["channel_id", "message_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "channel_id": { "type": "string" },
            "message_id": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
FROM discord_service ds

UNION ALL

-- Discord Reaction: Pin Message (executable)
SELECT
    ds.id,
    'pin_message',
    'Pin Message',
    'Pin a message in a channel',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "channel_id": { "type": "string", "description": "Channel id containing the message" },
            "message_id": { "type": "string", "description": "Message id to pin" }
        },
        "required": ["channel_id", "message_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "channel_id": { "type": "string" },
            "message_id": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
FROM discord_service ds

UNION ALL

-- Discord Reaction: Kick Member (executable)
SELECT
    ds.id,
    'kick_member',
    'Kick Member',
    'Kick a member from a guild',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "guild_id": { "type": "string", "description": "Guild id to kick from" },
            "user_id": { "type": "string", "description": "User id to kick" },
            "reason": { "type": "string", "description": "Reason for kicking (optional)" }
        },
        "required": ["guild_id", "user_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "guild_id": { "type": "string" },
            "user_id": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
FROM discord_service ds

UNION ALL

-- Discord Reaction: Ban Member (executable)
SELECT
    ds.id,
    'ban_member',
    'Ban Member',
    'Ban a member from a guild',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "guild_id": { "type": "string", "description": "Guild id to ban from" },
            "user_id": { "type": "string", "description": "User id to ban" },
            "reason": { "type": "string", "description": "Reason for banning (optional)" },
            "delete_message_days": { "type": "integer", "description": "Number of days of messages to delete (0-7, optional)", "default": 0 }
        },
        "required": ["guild_id", "user_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "guild_id": { "type": "string" },
            "user_id": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
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
