-- ============================================
-- AREA - Slack Service and Actions
-- Add Slack service and basic action/reaction definitions
-- ============================================

-- Add Slack service
INSERT INTO area.a_services (key, name, auth, is_active, docs_url, icon_light_url, icon_dark_url)
VALUES ('slack', 'Slack', 'OAUTH2', true, 'https://api.slack.com/docs',
        'https://cdn.simpleicons.org/slack/4A154B',
        'https://cdn.simpleicons.org/slack/FFFFFF')
ON CONFLICT (key) DO UPDATE SET
    name = EXCLUDED.name,
    auth = EXCLUDED.auth,
    is_active = EXCLUDED.is_active,
    docs_url = EXCLUDED.docs_url,
    icon_light_url = EXCLUDED.icon_light_url,
    icon_dark_url = EXCLUDED.icon_dark_url;

-- Get Slack service ID for references
WITH slack_service AS (
    SELECT id FROM area.a_services WHERE key = 'slack'
)

-- Insert Slack Actions (Event-capable) and Reactions (Executable)
INSERT INTO area.a_action_definitions (
    service_id, key, name, description,
    is_event_capable, is_executable, version,
    input_schema, output_schema,
    default_poll_interval_seconds
)

-- Slack: New Message (event)
SELECT
    ss.id,
    'new_message',
    'New Message',
    'Triggered when a new message is posted in a channel',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "channel": { "type": "string", "description": "Channel ID to monitor" }
        },
        "required": ["channel"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "channel": { "type": "string" },
            "text": { "type": "string" },
            "user": { "type": "string" },
            "ts": { "type": "string" },
            "type": { "type": "string" }
        }
    }'::jsonb,
    60
FROM slack_service ss

UNION ALL

-- Slack: New Channel (event)
SELECT
    ss.id,
    'new_channel',
    'New Channel',
    'Triggered when a new channel is created',
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
            "channel_id": { "type": "string" },
            "channel_name": { "type": "string" },
            "created": { "type": "number" },
            "type": { "type": "string" }
        }
    }'::jsonb,
    300
FROM slack_service ss

UNION ALL

-- Slack: User Joined Channel (event)
SELECT
    ss.id,
    'user_joined',
    'User Joined Channel',
    'Triggered when a user joins a channel',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "channel": { "type": "string", "description": "Channel ID to monitor" }
        },
        "required": ["channel"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "channel": { "type": "string" },
            "user": { "type": "string" },
            "type": { "type": "string" }
        }
    }'::jsonb,
    180
FROM slack_service ss

UNION ALL

-- Slack: Reaction Added (event)
SELECT
    ss.id,
    'reaction_added',
    'Reaction Added',
    'Triggered when a reaction is added to a message',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "channel": { "type": "string", "description": "Channel ID to monitor" }
        },
        "required": ["channel"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "channel": { "type": "string" },
            "message_ts": { "type": "string" },
            "emoji": { "type": "string" },
            "count": { "type": "number" },
            "type": { "type": "string" }
        }
    }'::jsonb,
    120
FROM slack_service ss

UNION ALL

-- Slack: File Shared (event)
SELECT
    ss.id,
    'file_shared',
    'File Shared',
    'Triggered when a file is shared in a channel',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "channel": { "type": "string", "description": "Channel ID to monitor" }
        },
        "required": ["channel"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "file_id": { "type": "string" },
            "file_name": { "type": "string" },
            "user": { "type": "string" },
            "timestamp": { "type": "string" },
            "type": { "type": "string" }
        }
    }'::jsonb,
    180
FROM slack_service ss

UNION ALL

-- Slack Reaction: Send Message (executable)
SELECT
    ss.id,
    'send_message',
    'Send Message',
    'Send a message to a Slack channel',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "channel": { "type": "string", "description": "Channel ID" },
            "text": { "type": "string", "description": "Message text" },
            "thread_ts": { "type": "string", "description": "Thread timestamp (optional)" },
            "blocks": { "type": "array", "description": "Block Kit blocks (optional)" }
        },
        "required": ["channel", "text"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "success": { "type": "boolean" },
            "channel": { "type": "string" },
            "ts": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM slack_service ss

UNION ALL

-- Slack Reaction: Create Channel (executable)
SELECT
    ss.id,
    'create_channel',
    'Create Channel',
    'Create a new Slack channel',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "name": { "type": "string", "description": "Channel name" },
            "is_private": { "type": "boolean", "description": "Private channel" }
        },
        "required": ["name"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "success": { "type": "boolean" },
            "channel_id": { "type": "string" },
            "channel_name": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM slack_service ss

UNION ALL

-- Slack Reaction: Add Reaction (executable)
SELECT
    ss.id,
    'add_reaction',
    'Add Reaction',
    'Add a reaction to a message',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "channel": { "type": "string", "description": "Channel ID" },
            "timestamp": { "type": "string", "description": "Message timestamp" },
            "emoji": { "type": "string", "description": "Emoji name" }
        },
        "required": ["channel", "timestamp", "emoji"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "success": { "type": "boolean" },
            "emoji": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM slack_service ss

UNION ALL

-- Slack Reaction: Pin Message (executable)
SELECT
    ss.id,
    'pin_message',
    'Pin Message',
    'Pin a message in a channel',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "channel": { "type": "string", "description": "Channel ID" },
            "timestamp": { "type": "string", "description": "Message timestamp" }
        },
        "required": ["channel", "timestamp"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "success": { "type": "boolean" }
        }
    }'::jsonb,
    NULL
FROM slack_service ss

UNION ALL

-- Slack Reaction: Set Status (executable)
SELECT
    ss.id,
    'set_status',
    'Set Status',
    'Set user status',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "status_text": { "type": "string", "description": "Status text" },
            "status_emoji": { "type": "string", "description": "Status emoji" }
        },
        "required": ["status_text"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "success": { "type": "boolean" },
            "status_text": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM slack_service ss

UNION ALL

-- Slack Reaction: Invite to Channel (executable)
SELECT
    ss.id,
    'invite_to_channel',
    'Invite to Channel',
    'Invite users to a channel',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "channel": { "type": "string", "description": "Channel ID" },
            "users": { "type": "string", "description": "Comma-separated user IDs" }
        },
        "required": ["channel", "users"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "success": { "type": "boolean" },
            "channel": { "type": "string" }
        }
    }'::jsonb,
    NULL
FROM slack_service ss

-- Update existing actions if they already exist
ON CONFLICT (service_id, key, version) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_event_capable = EXCLUDED.is_event_capable,
    is_executable = EXCLUDED.is_executable,
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    default_poll_interval_seconds = EXCLUDED.default_poll_interval_seconds;
