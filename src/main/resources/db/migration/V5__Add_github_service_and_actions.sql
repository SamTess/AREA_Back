-- ============================================
-- AREA - GitHub Service and Actions
-- Add GitHub service and basic action definitions
-- ============================================

-- Add GitHub service
INSERT INTO area.a_services (key, name, auth, is_active, docs_url) 
VALUES ('github', 'GitHub', 'OAUTH2', true, 'https://docs.github.com/en/rest')
ON CONFLICT (key) DO UPDATE SET
    name = EXCLUDED.name,
    auth = EXCLUDED.auth,
    is_active = EXCLUDED.is_active,
    docs_url = EXCLUDED.docs_url;

-- Get GitHub service ID for references
-- Note: In PostgreSQL, we'll use a CTE for this

WITH github_service AS (
    SELECT id FROM area.a_services WHERE key = 'github'
)

-- Insert GitHub Actions (Event-capable)
INSERT INTO area.a_action_definitions (
    service_id, key, name, description, 
    is_event_capable, is_executable, version,
    input_schema, output_schema,
    default_poll_interval_seconds
)
SELECT 
    gs.id,
    'new_issue',
    'New Issue Created',
    'Triggered when a new issue is created in a repository',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "repository": {
                "type": "string",
                "description": "Repository name in format owner/repo",
                "pattern": "^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$"
            },
            "labels": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Filter by issue labels (optional)"
            }
        },
        "required": ["repository"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "issue_number": {"type": "integer"},
            "title": {"type": "string"},
            "body": {"type": "string"},
            "author": {"type": "string"},
            "labels": {"type": "array", "items": {"type": "string"}},
            "created_at": {"type": "string", "format": "date-time"},
            "html_url": {"type": "string"}
        }
    }'::jsonb,
    300
FROM github_service gs

UNION ALL

SELECT 
    gs.id,
    'new_pull_request',
    'New Pull Request',
    'Triggered when a new pull request is created',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "repository": {
                "type": "string",
                "description": "Repository name in format owner/repo",
                "pattern": "^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$"
            },
            "target_branch": {
                "type": "string",
                "description": "Target branch name (optional)",
                "default": "main"
            }
        },
        "required": ["repository"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "pr_number": {"type": "integer"},
            "title": {"type": "string"},
            "body": {"type": "string"},
            "author": {"type": "string"},
            "source_branch": {"type": "string"},
            "target_branch": {"type": "string"},
            "created_at": {"type": "string", "format": "date-time"},
            "html_url": {"type": "string"}
        }
    }'::jsonb,
    300
FROM github_service gs

UNION ALL

SELECT 
    gs.id,
    'push_to_branch',
    'Push to Branch',
    'Triggered when commits are pushed to a branch',
    true,
    false,
    1,
    '{
        "type": "object",
        "properties": {
            "repository": {
                "type": "string",
                "description": "Repository name in format owner/repo",
                "pattern": "^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$"
            },
            "branch": {
                "type": "string",
                "description": "Branch name to monitor",
                "default": "main"
            }
        },
        "required": ["repository", "branch"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "commit_sha": {"type": "string"},
            "commit_message": {"type": "string"},
            "author": {"type": "string"},
            "branch": {"type": "string"},
            "pushed_at": {"type": "string", "format": "date-time"},
            "compare_url": {"type": "string"}
        }
    }'::jsonb,
    180
FROM github_service gs

UNION ALL

-- Insert GitHub Reactions (Executable)
SELECT 
    gs.id,
    'create_issue',
    'Create Issue',
    'Create a new issue in a repository',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "repository": {
                "type": "string",
                "description": "Repository name in format owner/repo",
                "pattern": "^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$"
            },
            "title": {
                "type": "string",
                "description": "Issue title",
                "minLength": 1,
                "maxLength": 255
            },
            "body": {
                "type": "string",
                "description": "Issue description"
            },
            "labels": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Labels to add to the issue"
            },
            "assignees": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Usernames to assign to the issue"
            }
        },
        "required": ["repository", "title"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "issue_number": {"type": "integer"},
            "html_url": {"type": "string"},
            "created_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM github_service gs

UNION ALL

SELECT 
    gs.id,
    'comment_issue',
    'Comment on Issue',
    'Add a comment to an existing issue',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "repository": {
                "type": "string",
                "description": "Repository name in format owner/repo",
                "pattern": "^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$"
            },
            "issue_number": {
                "type": "integer",
                "description": "Issue number",
                "minimum": 1
            },
            "comment": {
                "type": "string",
                "description": "Comment text",
                "minLength": 1
            }
        },
        "required": ["repository", "issue_number", "comment"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "comment_id": {"type": "integer"},
            "html_url": {"type": "string"},
            "created_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM github_service gs

UNION ALL

SELECT 
    gs.id,
    'close_issue',
    'Close Issue',
    'Close an existing issue',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "repository": {
                "type": "string",
                "description": "Repository name in format owner/repo",
                "pattern": "^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$"
            },
            "issue_number": {
                "type": "integer",
                "description": "Issue number",
                "minimum": 1
            },
            "comment": {
                "type": "string",
                "description": "Optional closing comment"
            }
        },
        "required": ["repository", "issue_number"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "issue_number": {"type": "integer"},
            "state": {"type": "string"},
            "closed_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM github_service gs

UNION ALL

SELECT 
    gs.id,
    'add_label',
    'Add Label to Issue',
    'Add labels to an existing issue or pull request',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "repository": {
                "type": "string",
                "description": "Repository name in format owner/repo",
                "pattern": "^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+$"
            },
            "issue_number": {
                "type": "integer",
                "description": "Issue or PR number",
                "minimum": 1
            },
            "labels": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Labels to add",
                "minItems": 1
            }
        },
        "required": ["repository", "issue_number", "labels"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "labels": {"type": "array", "items": {"type": "string"}},
            "updated_at": {"type": "string", "format": "date-time"}
        }
    }'::jsonb,
    null
FROM github_service gs

ON CONFLICT (service_id, key, version) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_event_capable = EXCLUDED.is_event_capable,
    is_executable = EXCLUDED.is_executable,
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    default_poll_interval_seconds = EXCLUDED.default_poll_interval_seconds,
    updated_at = now();