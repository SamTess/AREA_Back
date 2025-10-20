-- ============================================
-- AREA - Google Service and Actions
-- Add Google service and various action definitions
-- ============================================

-- Add Google service
INSERT INTO area.a_services (key, name, auth, is_active, docs_url, icon_light_url, icon_dark_url) 
VALUES ('google', 'Google', 'OAUTH2', true, 'https://developers.google.com/apis-explorer', 
        'https://img.icons8.com/?size=100&id=17949&format=png&color=000000', 
        'https://img.icons8.com/?size=100&id=17949&format=png&color=000000')
ON CONFLICT (key) DO UPDATE SET
    name = EXCLUDED.name,
    auth = EXCLUDED.auth,
    is_active = EXCLUDED.is_active,
    docs_url = EXCLUDED.docs_url,
    icon_light_url = EXCLUDED.icon_light_url,
    icon_dark_url = EXCLUDED.icon_dark_url;

-- Get Google service ID for references
WITH google_service AS (
    SELECT id FROM area.a_services WHERE key = 'google'
)

-- Insert Google Actions (Event-capable) and Reactions (Executable)
INSERT INTO area.a_action_definitions (
    service_id, key, name, description,
    is_event_capable, is_executable, version,
    input_schema, output_schema,
    default_poll_interval_seconds
)

-- ============================================
-- GMAIL ACTIONS (Event-capable)
-- ============================================
SELECT
    gs.id,
    'gmail_new_email',
    'New Email Received',
    'Triggered when a new email is received in Gmail',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "label": {
                "type": "string",
                "description": "Gmail label to monitor (e.g., INBOX, SENT)",
                "default": "INBOX"
            },
            "from": {
                "type": "string",
                "description": "Filter by sender email address (optional)"
            },
            "subject_contains": {
                "type": "string",
                "description": "Filter by subject keywords (optional)"
            }
        },
        "required": []
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "message_id": {"type": "string"},
            "thread_id": {"type": "string"},
            "from": {"type": "string"},
            "to": {"type": "array", "items": {"type": "string"}},
            "subject": {"type": "string"},
            "snippet": {"type": "string"},
            "body": {"type": "string"},
            "received_at": {"type": "string", "format": "date-time"},
            "labels": {"type": "array", "items": {"type": "string"}}
        }
    }'::jsonb,
    60
FROM google_service gs

UNION ALL

-- ============================================
-- GOOGLE CALENDAR ACTIONS (Event-capable)
-- ============================================
SELECT
    gs.id,
    'calendar_new_event',
    'New Calendar Event',
    'Triggered when a new event is created in Google Calendar',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "calendar_id": {
                "type": "string",
                "description": "Calendar ID (default: primary)",
                "default": "primary"
            }
        },
        "required": []
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "event_id": {"type": "string"},
            "summary": {"type": "string"},
            "description": {"type": "string"},
            "location": {"type": "string"},
            "start_time": {"type": "string", "format": "date-time"},
            "end_time": {"type": "string", "format": "date-time"},
            "attendees": {"type": "array", "items": {"type": "string"}},
            "created_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    300
FROM google_service gs

UNION ALL

SELECT
    gs.id,
    'calendar_event_starting',
    'Calendar Event Starting Soon',
    'Triggered before a calendar event starts',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "calendar_id": {
                "type": "string",
                "description": "Calendar ID (default: primary)",
                "default": "primary"
            },
            "minutes_before": {
                "type": "integer",
                "description": "Minutes before event starts",
                "default": 15,
                "minimum": 1,
                "maximum": 1440
            }
        },
        "required": []
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "event_id": {"type": "string"},
            "summary": {"type": "string"},
            "description": {"type": "string"},
            "location": {"type": "string"},
            "start_time": {"type": "string", "format": "date-time"},
            "end_time": {"type": "string", "format": "date-time"},
            "time_until_start": {"type": "integer"}
        }
    }'::jsonb,
    120
FROM google_service gs

UNION ALL

-- ============================================
-- GOOGLE DRIVE ACTIONS (Event-capable)
-- ============================================
SELECT
    gs.id,
    'drive_new_file',
    'New File in Drive',
    'Triggered when a new file is created in Google Drive',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "folder_id": {
                "type": "string",
                "description": "Specific folder ID to monitor (optional)"
            },
            "file_type": {
                "type": "string",
                "description": "Filter by MIME type (optional)",
                "enum": ["document", "spreadsheet", "presentation", "folder", "pdf", "image", "any"]
            }
        },
        "required": []
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "file_id": {"type": "string"},
            "name": {"type": "string"},
            "mime_type": {"type": "string"},
            "web_view_link": {"type": "string"},
            "created_time": {"type": "string", "format": "date-time"},
            "modified_time": {"type": "string", "format": "date-time"},
            "size": {"type": "integer"},
            "owner": {"type": "string"}
        }
    }'::jsonb,
    300
FROM google_service gs

UNION ALL

SELECT
    gs.id,
    'drive_file_modified',
    'File Modified in Drive',
    'Triggered when a file is modified in Google Drive',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "folder_id": {
                "type": "string",
                "description": "Specific folder ID to monitor (optional)"
            },
            "file_id": {
                "type": "string",
                "description": "Specific file ID to monitor (optional)"
            }
        },
        "required": []
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "file_id": {"type": "string"},
            "name": {"type": "string"},
            "mime_type": {"type": "string"},
            "web_view_link": {"type": "string"},
            "modified_time": {"type": "string", "format": "date-time"},
            "modified_by": {"type": "string"}
        }
    }'::jsonb,
    180
FROM google_service gs

UNION ALL

-- ============================================
-- GOOGLE SHEETS ACTIONS (Event-capable)
-- ============================================
SELECT
    gs.id,
    'sheets_row_added',
    'New Row Added to Sheet',
    'Triggered when a new row is added to a Google Sheet',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "spreadsheet_id": {
                "type": "string",
                "description": "Google Spreadsheet ID"
            },
            "sheet_name": {
                "type": "string",
                "description": "Sheet name/tab",
                "default": "Sheet1"
            },
            "range": {
                "type": "string",
                "description": "Range to monitor (e.g., A:Z)",
                "default": "A:Z"
            }
        },
        "required": ["spreadsheet_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "row_number": {"type": "integer"},
            "values": {"type": "array", "items": {"type": "string"}},
            "spreadsheet_id": {"type": "string"},
            "sheet_name": {"type": "string"},
            "timestamp": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    120
FROM google_service gs

UNION ALL

-- ============================================
-- GMAIL REACTIONS (Executable)
-- ============================================
SELECT
    gs.id,
    'gmail_send_email',
    'Send Gmail',
    'Send an email via Gmail',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "to": {
                "type": "string",
                "description": "Recipient email address",
                "format": "email"
            },
            "subject": {
                "type": "string",
                "description": "Email subject",
                "minLength": 1,
                "maxLength": 255
            },
            "body": {
                "type": "string",
                "description": "Email body (plain text or HTML)"
            },
            "cc": {
                "type": "string",
                "description": "CC email addresses (comma-separated)"
            },
            "bcc": {
                "type": "string",
                "description": "BCC email addresses (comma-separated)"
            }
        },
        "required": ["to", "subject", "body"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "message_id": {"type": "string"},
            "thread_id": {"type": "string"},
            "sent_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM google_service gs

UNION ALL

SELECT
    gs.id,
    'gmail_add_label',
    'Add Gmail Label',
    'Add a label to an email in Gmail',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "message_id": {
                "type": "string",
                "description": "Gmail message ID"
            },
            "label_name": {
                "type": "string",
                "description": "Label name to add"
            }
        },
        "required": ["message_id", "label_name"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "message_id": {"type": "string"},
            "labels": {"type": "array", "items": {"type": "string"}},
            "updated_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM google_service gs

UNION ALL

-- ============================================
-- GOOGLE CALENDAR REACTIONS (Executable)
-- ============================================
SELECT
    gs.id,
    'calendar_create_event',
    'Create Calendar Event',
    'Create a new event in Google Calendar',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "calendar_id": {
                "type": "string",
                "description": "Calendar ID (default: primary)",
                "default": "primary"
            },
            "summary": {
                "type": "string",
                "description": "Event title",
                "minLength": 1,
                "maxLength": 255
            },
            "description": {
                "type": "string",
                "description": "Event description"
            },
            "location": {
                "type": "string",
                "description": "Event location"
            },
            "start_time": {
                "type": "string",
                "description": "Start time (ISO 8601)",
                "format": "date-time"
            },
            "end_time": {
                "type": "string",
                "description": "End time (ISO 8601)",
                "format": "date-time"
            },
            "attendees": {
                "type": "array",
                "items": {"type": "string", "format": "email"},
                "description": "List of attendee email addresses"
            }
        },
        "required": ["summary", "start_time", "end_time"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "event_id": {"type": "string"},
            "html_link": {"type": "string"},
            "created_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM google_service gs

UNION ALL

SELECT
    gs.id,
    'calendar_delete_event',
    'Delete Calendar Event',
    'Delete an event from Google Calendar',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "calendar_id": {
                "type": "string",
                "description": "Calendar ID (default: primary)",
                "default": "primary"
            },
            "event_id": {
                "type": "string",
                "description": "Event ID to delete"
            }
        },
        "required": ["event_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "event_id": {"type": "string"},
            "deleted": {"type": "boolean"},
            "deleted_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM google_service gs

UNION ALL

-- ============================================
-- GOOGLE DRIVE REACTIONS (Executable)
-- ============================================
SELECT
    gs.id,
    'drive_create_folder',
    'Create Drive Folder',
    'Create a new folder in Google Drive',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "name": {
                "type": "string",
                "description": "Folder name",
                "minLength": 1,
                "maxLength": 255
            },
            "parent_folder_id": {
                "type": "string",
                "description": "Parent folder ID (optional, defaults to root)"
            }
        },
        "required": ["name"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "folder_id": {"type": "string"},
            "name": {"type": "string"},
            "web_view_link": {"type": "string"},
            "created_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM google_service gs

UNION ALL

SELECT
    gs.id,
    'drive_upload_file',
    'Upload File to Drive',
    'Upload a file to Google Drive',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "name": {
                "type": "string",
                "description": "File name",
                "minLength": 1,
                "maxLength": 255
            },
            "content": {
                "type": "string",
                "description": "File content (base64 encoded)"
            },
            "mime_type": {
                "type": "string",
                "description": "File MIME type",
                "default": "text/plain"
            },
            "parent_folder_id": {
                "type": "string",
                "description": "Parent folder ID (optional)"
            }
        },
        "required": ["name", "content"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "file_id": {"type": "string"},
            "name": {"type": "string"},
            "web_view_link": {"type": "string"},
            "created_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM google_service gs

UNION ALL

SELECT
    gs.id,
    'drive_share_file',
    'Share Drive File',
    'Share a file in Google Drive with specific users',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "file_id": {
                "type": "string",
                "description": "File ID to share"
            },
            "email": {
                "type": "string",
                "description": "Email address to share with",
                "format": "email"
            },
            "role": {
                "type": "string",
                "description": "Permission role",
                "enum": ["reader", "writer", "commenter", "owner"],
                "default": "reader"
            }
        },
        "required": ["file_id", "email"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "permission_id": {"type": "string"},
            "email": {"type": "string"},
            "role": {"type": "string"},
            "shared_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM google_service gs

UNION ALL

-- ============================================
-- GOOGLE SHEETS REACTIONS (Executable)
-- ============================================
SELECT
    gs.id,
    'sheets_add_row',
    'Add Row to Sheet',
    'Add a new row to a Google Sheet',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "spreadsheet_id": {
                "type": "string",
                "description": "Google Spreadsheet ID"
            },
            "sheet_name": {
                "type": "string",
                "description": "Sheet name/tab",
                "default": "Sheet1"
            },
            "values": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Array of cell values"
            }
        },
        "required": ["spreadsheet_id", "values"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "spreadsheet_id": {"type": "string"},
            "sheet_name": {"type": "string"},
            "updated_range": {"type": "string"},
            "updated_rows": {"type": "integer"},
            "updated_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM google_service gs

UNION ALL

SELECT
    gs.id,
    'sheets_update_cell',
    'Update Sheet Cell',
    'Update a specific cell in a Google Sheet',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "spreadsheet_id": {
                "type": "string",
                "description": "Google Spreadsheet ID"
            },
            "sheet_name": {
                "type": "string",
                "description": "Sheet name/tab",
                "default": "Sheet1"
            },
            "cell": {
                "type": "string",
                "description": "Cell reference (e.g., A1, B5)",
                "pattern": "^[A-Z]+[0-9]+$"
            },
            "value": {
                "type": "string",
                "description": "New cell value"
            }
        },
        "required": ["spreadsheet_id", "cell", "value"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "spreadsheet_id": {"type": "string"},
            "updated_range": {"type": "string"},
            "updated_cells": {"type": "integer"},
            "updated_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM google_service gs

UNION ALL

SELECT
    gs.id,
    'sheets_create_spreadsheet',
    'Create New Spreadsheet',
    'Create a new Google Spreadsheet',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "title": {
                "type": "string",
                "description": "Spreadsheet title",
                "minLength": 1,
                "maxLength": 255
            },
            "sheet_names": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Names of sheets to create"
            }
        },
        "required": ["title"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "spreadsheet_id": {"type": "string"},
            "spreadsheet_url": {"type": "string"},
            "created_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM google_service gs

ON CONFLICT (service_id, key, version) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_event_capable = EXCLUDED.is_event_capable,
    is_executable = EXCLUDED.is_executable,
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    default_poll_interval_seconds = EXCLUDED.default_poll_interval_seconds,
    updated_at = now();
