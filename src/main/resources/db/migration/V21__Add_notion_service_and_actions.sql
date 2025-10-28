-- ============================================
-- AREA - Notion Service and Actions
-- Add Notion service and basic action/reaction definitions
-- ============================================

-- Add Notion service
INSERT INTO area.a_services (key, name, auth, is_active, docs_url, icon_light_url, icon_dark_url)
VALUES ('notion', 'Notion', 'OAUTH2', true, 'https://developers.notion.com/reference',
        'https://img.icons8.com/color/96/notion--v1.png',
        'https://img.icons8.com/color/96/notion--v1.png')
ON CONFLICT (key) DO UPDATE SET
    name = EXCLUDED.name,
    auth = EXCLUDED.auth,
    is_active = EXCLUDED.is_active,
    docs_url = EXCLUDED.docs_url,
    icon_light_url = EXCLUDED.icon_light_url,
    icon_dark_url = EXCLUDED.icon_dark_url;

-- Get Notion service ID for references
WITH notion_service AS (
    SELECT id FROM area.a_services WHERE key = 'notion'
)

-- Insert Notion Actions (Event-capable) and Reactions (Executable)
INSERT INTO area.a_action_definitions (
    service_id, key, name, description,
    is_event_capable, is_executable, version,
    input_schema, output_schema,
    default_poll_interval_seconds
)

-- Notion Trigger: New Page Created
SELECT
    ns.id,
    'new_page',
    'New Page Created',
    'Triggered when a new page is created in a database or workspace',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "database_id": {
                "type": "string",
                "description": "Database ID to monitor (optional, monitors all accessible pages if not specified)"
            }
        }
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "title": { "type": "string" },
            "url": { "type": "string" },
            "created_time": { "type": "string" },
            "created_by": { "type": "string" },
            "parent_type": { "type": "string" },
            "parent_id": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
FROM notion_service ns

UNION ALL

-- Notion Trigger: Page Updated
SELECT
    ns.id,
    'page_updated',
    'Page Updated',
    'Triggered when a page is updated in a database or workspace',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "database_id": {
                "type": "string",
                "description": "Database ID to monitor (optional)"
            },
            "page_id": {
                "type": "string",
                "description": "Specific page ID to monitor (optional)"
            }
        }
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "title": { "type": "string" },
            "url": { "type": "string" },
            "last_edited_time": { "type": "string" },
            "last_edited_by": { "type": "string" },
            "properties": { "type": "object" }
        }
    }'::jsonb,
    NULL::integer
FROM notion_service ns

UNION ALL

-- Notion Trigger: New Database Item
SELECT
    ns.id,
    'new_database_item',
    'New Database Item',
    'Triggered when a new item is added to a specific database',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "database_id": {
                "type": "string",
                "description": "Database ID to monitor"
            }
        },
        "required": ["database_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "title": { "type": "string" },
            "url": { "type": "string" },
            "created_time": { "type": "string" },
            "properties": { "type": "object" }
        }
    }'::jsonb,
    NULL::integer
FROM notion_service ns

UNION ALL

-- Notion Action: Create Page
SELECT
    ns.id,
    'create_page',
    'Create Page',
    'Create a new page in Notion',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "parent_id": {
                "type": "string",
                "description": "Parent page or database ID"
            },
            "parent_type": {
                "type": "string",
                "enum": ["page_id", "database_id"],
                "description": "Type of parent"
            },
            "title": {
                "type": "string",
                "description": "Page title"
            },
            "content": {
                "type": "string",
                "description": "Page content (plain text)"
            },
            "icon": {
                "type": "string",
                "description": "Page icon emoji (optional)"
            }
        },
        "required": ["parent_id", "parent_type", "title"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "url": { "type": "string" },
            "created_time": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
FROM notion_service ns

UNION ALL

-- Notion Action: Update Page
SELECT
    ns.id,
    'update_page',
    'Update Page',
    'Update an existing Notion page',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "page_id": {
                "type": "string",
                "description": "Page ID to update"
            },
            "title": {
                "type": "string",
                "description": "New page title (optional)"
            },
            "archived": {
                "type": "boolean",
                "description": "Archive the page (optional)"
            },
            "icon": {
                "type": "string",
                "description": "New page icon emoji (optional)"
            },
            "properties": {
                "type": "object",
                "description": "Properties to update (for database pages)"
            }
        },
        "required": ["page_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "url": { "type": "string" },
            "last_edited_time": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
FROM notion_service ns

UNION ALL

-- Notion Action: Create Database Item
SELECT
    ns.id,
    'create_database_item',
    'Create Database Item',
    'Create a new item in a Notion database',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "database_id": {
                "type": "string",
                "description": "Database ID"
            },
            "properties": {
                "type": "object",
                "description": "Database properties (key-value pairs)",
                "additionalProperties": true
            }
        },
        "required": ["database_id", "properties"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "url": { "type": "string" },
            "created_time": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
FROM notion_service ns

UNION ALL

-- Notion Action: Update Database Item
SELECT
    ns.id,
    'update_database_item',
    'Update Database Item',
    'Update an existing database item in Notion',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "page_id": {
                "type": "string",
                "description": "Database item (page) ID to update"
            },
            "properties": {
                "type": "object",
                "description": "Properties to update (key-value pairs)",
                "additionalProperties": true
            }
        },
        "required": ["page_id", "properties"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "url": { "type": "string" },
            "last_edited_time": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
FROM notion_service ns

UNION ALL

-- Notion Action: Archive Page
SELECT
    ns.id,
    'archive_page',
    'Archive Page',
    'Archive a Notion page or database item',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "page_id": {
                "type": "string",
                "description": "Page ID to archive"
            }
        },
        "required": ["page_id"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "archived": { "type": "boolean" },
            "last_edited_time": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
FROM notion_service ns

UNION ALL

-- Notion Action: Add Comment
SELECT
    ns.id,
    'add_comment',
    'Add Comment',
    'Add a comment to a Notion page',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "page_id": {
                "type": "string",
                "description": "Page ID to comment on"
            },
            "comment": {
                "type": "string",
                "description": "Comment text"
            }
        },
        "required": ["page_id", "comment"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "comment_id": { "type": "string" },
            "created_time": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
FROM notion_service ns

UNION ALL

-- Notion Action: Search Pages
SELECT
    ns.id,
    'search_pages',
    'Search Pages',
    'Search for pages in Notion workspace',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "Search query"
            },
            "filter_type": {
                "type": "string",
                "enum": ["page", "database"],
                "description": "Filter by object type (optional)"
            },
            "max_results": {
                "type": "integer",
                "description": "Maximum number of results (default: 10)"
            }
        },
        "required": ["query"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "results": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "page_id": { "type": "string" },
                        "title": { "type": "string" },
                        "url": { "type": "string" }
                    }
                }
            },
            "has_more": { "type": "boolean" }
        }
    }'::jsonb,
    NULL::integer
FROM notion_service ns

UNION ALL

-- Notion Action: Create Database
SELECT
    ns.id,
    'create_database',
    'Create Database',
    'Create a new database in Notion',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "parent_page_id": {
                "type": "string",
                "description": "Parent page ID"
            },
            "title": {
                "type": "string",
                "description": "Database title"
            },
            "properties": {
                "type": "object",
                "description": "Database schema properties",
                "additionalProperties": true
            }
        },
        "required": ["parent_page_id", "title"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "database_id": { "type": "string" },
            "url": { "type": "string" },
            "created_time": { "type": "string" }
        }
    }'::jsonb,
    NULL::integer
FROM notion_service ns

-- Update existing actions if they already exist
ON CONFLICT (service_id, key, version) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_event_capable = EXCLUDED.is_event_capable,
    is_executable = EXCLUDED.is_executable,
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    default_poll_interval_seconds = EXCLUDED.default_poll_interval_seconds;
