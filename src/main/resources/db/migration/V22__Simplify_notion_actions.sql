-- ============================================
-- AREA - Simplify Notion Actions
-- Update Notion action schemas with simpler parameters
-- ============================================

-- Update Create Page action - simplified to just page_id, title, content
UPDATE area.a_action_definitions
SET
    input_schema = '{
        "type": "object",
        "properties": {
            "page_id": {
                "type": "string",
                "description": "Parent page ID"
            },
            "title": {
                "type": "string",
                "description": "Page title"
            },
            "content": {
                "type": "string",
                "description": "Page content (optional)"
            }
        },
        "required": ["page_id", "title"]
    }'::jsonb,
    output_schema = '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "url": { "type": "string" }
        }
    }'::jsonb,
    description = 'Create a new page under another page'
WHERE key = 'create_page'
AND service_id = (SELECT id FROM area.a_services WHERE key = 'notion');

-- Update Update Page action - simplified to just page_id, title, content
UPDATE area.a_action_definitions
SET
    input_schema = '{
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
            "content": {
                "type": "string",
                "description": "Content to add to page (optional)"
            }
        },
        "required": ["page_id"]
    }'::jsonb,
    output_schema = '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "url": { "type": "string" }
        }
    }'::jsonb,
    description = 'Update a page title and/or add content'
WHERE key = 'update_page'
AND service_id = (SELECT id FROM area.a_services WHERE key = 'notion');

-- Update Create Database Item - simplified to just database_id and name
UPDATE area.a_action_definitions
SET
    input_schema = '{
        "type": "object",
        "properties": {
            "database_id": {
                "type": "string",
                "description": "Database ID"
            },
            "name": {
                "type": "string",
                "description": "Item name/title"
            }
        },
        "required": ["database_id", "name"]
    }'::jsonb,
    output_schema = '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "url": { "type": "string" }
        }
    }'::jsonb,
    description = 'Create a simple database item with a name'
WHERE key = 'create_database_item'
AND service_id = (SELECT id FROM area.a_services WHERE key = 'notion');

-- Update Update Database Item - simplified to just page_id and name
UPDATE area.a_action_definitions
SET
    input_schema = '{
        "type": "object",
        "properties": {
            "page_id": {
                "type": "string",
                "description": "Database item (page) ID to update"
            },
            "name": {
                "type": "string",
                "description": "New item name/title"
            }
        },
        "required": ["page_id", "name"]
    }'::jsonb,
    output_schema = '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "url": { "type": "string" }
        }
    }'::jsonb,
    description = 'Update a database item name'
WHERE key = 'update_database_item'
AND service_id = (SELECT id FROM area.a_services WHERE key = 'notion');

-- Update Archive Page - output simplified
UPDATE area.a_action_definitions
SET
    output_schema = '{
        "type": "object",
        "properties": {
            "page_id": { "type": "string" },
            "archived": { "type": "boolean" }
        }
    }'::jsonb
WHERE key = 'archive_page'
AND service_id = (SELECT id FROM area.a_services WHERE key = 'notion');

-- Update Add Comment - output simplified
UPDATE area.a_action_definitions
SET
    output_schema = '{
        "type": "object",
        "properties": {
            "comment_id": { "type": "string" }
        }
    }'::jsonb
WHERE key = 'add_comment'
AND service_id = (SELECT id FROM area.a_services WHERE key = 'notion');

-- Update Search Pages - simplified input/output
UPDATE area.a_action_definitions
SET
    input_schema = '{
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "Search query"
            }
        },
        "required": ["query"]
    }'::jsonb,
    output_schema = '{
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
            }
        }
    }'::jsonb,
    description = 'Search for pages (returns max 5 results)'
WHERE key = 'search_pages'
AND service_id = (SELECT id FROM area.a_services WHERE key = 'notion');

-- Update Create Database - simplified input/output
UPDATE area.a_action_definitions
SET
    input_schema = '{
        "type": "object",
        "properties": {
            "page_id": {
                "type": "string",
                "description": "Parent page ID"
            },
            "title": {
                "type": "string",
                "description": "Database title"
            }
        },
        "required": ["page_id", "title"]
    }'::jsonb,
    output_schema = '{
        "type": "object",
        "properties": {
            "database_id": { "type": "string" },
            "url": { "type": "string" }
        }
    }'::jsonb,
    description = 'Create a simple database with a Name property'
WHERE key = 'create_database'
AND service_id = (SELECT id FROM area.a_services WHERE key = 'notion');
