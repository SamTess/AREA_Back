# Database Migrations Guide

## Table of Contents
- [Overview](#overview)
- [Flyway Basics](#flyway-basics)
- [Migration Structure](#migration-structure)
- [Creating Migrations](#creating-migrations)
- [Naming Conventions](#naming-conventions)
- [Migration Types](#migration-types)
- [Best Practices](#best-practices)
- [Common Patterns](#common-patterns)
- [Rollback Strategy](#rollback-strategy)
- [Testing Migrations](#testing-migrations)
- [Troubleshooting](#troubleshooting)

## Overview

The AREA Backend uses **Flyway** for database schema versioning and migration management. Flyway ensures that all database changes are tracked, versioned, and applied consistently across all environments.

### Key Benefits

- **Version Control**: All schema changes are versioned and tracked
- **Repeatability**: Migrations can be applied consistently across environments
- **Safety**: Checksums prevent accidental modifications
- **Audit Trail**: Complete history of database changes
- **Team Collaboration**: Multiple developers can work on schema changes

### Configuration

Flyway is configured in `application.yml`:

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    schemas: area
    table: flyway_schema_history
    validate-on-migrate: true
```

## Flyway Basics

### How Flyway Works

1. **Initialization**: Flyway creates a metadata table (`flyway_schema_history`)
2. **Discovery**: Scans `src/main/resources/db/migration` for migration files
3. **Validation**: Checks checksums of applied migrations
4. **Execution**: Applies pending migrations in order
5. **Recording**: Records successful migrations in metadata table

### Metadata Table

Flyway tracks migrations in the `flyway_schema_history` table:

```sql
SELECT * FROM area.flyway_schema_history;
```

| Column | Description |
|--------|-------------|
| `installed_rank` | Order of execution |
| `version` | Migration version |
| `description` | Migration description |
| `type` | Migration type (SQL, JAVA) |
| `script` | Migration file name |
| `checksum` | File checksum |
| `installed_by` | Database user |
| `installed_on` | Execution timestamp |
| `execution_time` | Execution duration (ms) |
| `success` | Success status |

## Migration Structure

### Directory Layout

```
src/main/resources/db/migration/
├── V1__Initial_schema.sql
├── V2__Fix_action_definition_description_column.sql
├── V3__Change_all_enum_columns_to_varchar.sql
├── V4__Fix_execution_area_trigger.sql
├── V5__Add_github_service_and_actions.sql
├── V6__Add_jsonb_actions_reactions_to_areas.sql
├── V7__Add_github_personal_token.sql
├── V8__Add_link_type_to_action_links.sql
├── V9__Fix_action_links_trigger_schema.sql
├── V10__Add_firstname_lastname_to_users.sql
├── V11__Add_google_service_and_actions.sql
├── V12__Add_discord_service_and_actions.sql (planned)
└── V13__Add_slack_service_and_actions.sql (planned)
```

### Migration File Structure

```sql
-- ============================================
-- Description: Brief description of changes
-- Author: Your Name
-- Date: 2024-01-15
-- ============================================

-- Clear comments explaining the purpose
CREATE TABLE IF NOT EXISTS area.example_table (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Add indexes
CREATE INDEX IF NOT EXISTS idx_example_table_name 
    ON area.example_table(name);

-- Insert initial data if needed
INSERT INTO area.example_table (name) 
VALUES ('Example') 
ON CONFLICT (name) DO NOTHING;
```

## Creating Migrations

### Step-by-Step Process

#### 1. Determine Version Number

Check the latest migration version:

```bash
ls -1 src/main/resources/db/migration/ | sort -V | tail -1
```

Increment the version number (e.g., V11 → V12).

#### 2. Create Migration File

```bash
# File naming format: V{version}__{description}.sql
touch src/main/resources/db/migration/V12__Add_discord_service_and_actions.sql
```

#### 3. Write Migration SQL

```sql
-- ============================================
-- AREA - Discord Service and Actions
-- Add Discord service and action definitions
-- ============================================

-- Add Discord service
INSERT INTO area.a_services (key, name, auth, is_active, docs_url, icon_light_url, icon_dark_url) 
VALUES (
    'discord',
    'Discord',
    'OAUTH2',
    true,
    'https://discord.com/developers/docs',
    'https://cdn.simpleicons.org/discord/5865F2',
    'https://cdn.simpleicons.org/discord/FFFFFF'
)
ON CONFLICT (key) DO UPDATE SET
    name = EXCLUDED.name,
    auth = EXCLUDED.auth,
    is_active = EXCLUDED.is_active,
    docs_url = EXCLUDED.docs_url,
    icon_light_url = EXCLUDED.icon_light_url,
    icon_dark_url = EXCLUDED.icon_dark_url;

-- Get Discord service ID for action definitions
WITH discord_service AS (
    SELECT id FROM area.a_services WHERE key = 'discord'
)

-- Insert Discord Actions
INSERT INTO area.a_action_definitions (
    service_id, key, name, description,
    is_event_capable, is_executable, version,
    input_schema, output_schema
)
SELECT
    ds.id,
    'send_message',
    'Send Message',
    'Send a message to a Discord channel',
    false,
    true,
    1,
    '{
        "type": "object",
        "properties": {
            "channel_id": {"type": "string"},
            "content": {"type": "string"}
        },
        "required": ["channel_id", "content"]
    }'::jsonb,
    '{
        "type": "object",
        "properties": {
            "message_id": {"type": "string"}
        }
    }'::jsonb
FROM discord_service ds
ON CONFLICT (service_id, key) DO NOTHING;
```

#### 4. Test Migration Locally

```bash
# Start database
docker-compose up -d postgres

# Run application (Flyway will execute migrations)
./gradlew bootRun

# Or run Flyway directly
./gradlew flywayMigrate
```

#### 5. Verify Migration

```sql
-- Check migration was applied
SELECT * FROM area.flyway_schema_history 
WHERE version = '12' 
ORDER BY installed_rank DESC 
LIMIT 1;

-- Verify data
SELECT * FROM area.a_services WHERE key = 'discord';
SELECT * FROM area.a_action_definitions WHERE service_id IN (
    SELECT id FROM area.a_services WHERE key = 'discord'
);
```

## Naming Conventions

### Version-Based Migrations (Recommended)

Format: `V{version}__{description}.sql`

**Rules**:
- Version must be numeric (e.g., V1, V2, V10, V100)
- Use double underscore `__` to separate version from description
- Description uses snake_case with underscores
- No spaces in filename

**Examples**:
```
✅ V1__Initial_schema.sql
✅ V12__Add_discord_service_and_actions.sql
✅ V100__Major_refactoring.sql

❌ V1.2__Invalid.sql                    # No dots in version
❌ V12_Single_underscore.sql            # Need double underscore
❌ V12__Add Discord Service.sql         # No spaces
❌ v12__lowercase_version.sql           # Version must be uppercase V
```

### Repeatable Migrations (Advanced)

Format: `R__{description}.sql`

Used for views, procedures, functions that can be re-applied:

```
R__Create_user_statistics_view.sql
R__Update_aggregation_functions.sql
```

## Migration Types

### 1. Schema Migrations

Create or modify database structure:

```sql
-- Create table
CREATE TABLE IF NOT EXISTS area.new_table (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL
);

-- Add column
ALTER TABLE area.existing_table 
ADD COLUMN IF NOT EXISTS new_column text;

-- Modify column
ALTER TABLE area.existing_table 
ALTER COLUMN existing_column TYPE varchar(255);

-- Drop column (use carefully!)
ALTER TABLE area.existing_table 
DROP COLUMN IF EXISTS old_column;
```

### 2. Data Migrations

Insert, update, or transform data:

```sql
-- Insert reference data
INSERT INTO area.a_services (key, name, auth, is_active)
VALUES ('new_service', 'New Service', 'OAUTH2', true)
ON CONFLICT (key) DO NOTHING;

-- Update existing data
UPDATE area.a_users 
SET is_active = true 
WHERE is_active IS NULL;

-- Transform data
UPDATE area.a_action_definitions 
SET input_schema = input_schema || '{"additionalProperties": false}'::jsonb
WHERE input_schema IS NOT NULL;
```

### 3. Index Migrations

Add or modify indexes for performance:

```sql
-- Create index
CREATE INDEX IF NOT EXISTS idx_users_email 
ON area.a_users(email);

-- Create composite index
CREATE INDEX IF NOT EXISTS idx_service_accounts_user_service 
ON area.a_service_accounts(user_id, service_id);

-- Create partial index
CREATE INDEX IF NOT EXISTS idx_active_areas 
ON area.a_areas(user_id) 
WHERE is_active = true;

-- Drop index
DROP INDEX IF EXISTS area.idx_old_index;
```

### 4. Constraint Migrations

Add or modify constraints:

```sql
-- Add foreign key
ALTER TABLE area.child_table 
ADD CONSTRAINT fk_child_parent 
FOREIGN KEY (parent_id) 
REFERENCES area.parent_table(id) 
ON DELETE CASCADE;

-- Add unique constraint
ALTER TABLE area.a_services 
ADD CONSTRAINT uq_services_key 
UNIQUE (key);

-- Add check constraint
ALTER TABLE area.a_users 
ADD CONSTRAINT chk_email_format 
CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}$');
```

## Best Practices

### 1. Idempotent Migrations

Always write migrations that can be re-run safely:

```sql
-- ✅ Good: Idempotent
CREATE TABLE IF NOT EXISTS area.example (...);
ALTER TABLE area.example ADD COLUMN IF NOT EXISTS col text;
INSERT INTO area.example VALUES (...) ON CONFLICT DO NOTHING;

-- ❌ Bad: Not idempotent
CREATE TABLE area.example (...);  -- Fails if table exists
ALTER TABLE area.example ADD COLUMN col text;  -- Fails if column exists
INSERT INTO area.example VALUES (...);  -- May create duplicates
```

### 2. Use Transactions

Most migrations run in a transaction by default:

```sql
-- Explicit transaction (if needed)
BEGIN;

-- Your migration statements
CREATE TABLE ...;
INSERT INTO ...;

COMMIT;
```

### 3. Handle Existing Data

When modifying schema, handle existing data:

```sql
-- Add NOT NULL column with default
ALTER TABLE area.a_users 
ADD COLUMN email_verified boolean DEFAULT false NOT NULL;

-- Or in two steps
ALTER TABLE area.a_users ADD COLUMN email_verified boolean;
UPDATE area.a_users SET email_verified = false WHERE email_verified IS NULL;
ALTER TABLE area.a_users ALTER COLUMN email_verified SET NOT NULL;
```

### 4. Use CTEs for Complex Migrations

```sql
-- Use CTE for readability
WITH service AS (
    SELECT id FROM area.a_services WHERE key = 'github'
)
INSERT INTO area.a_action_definitions (service_id, key, name, ...)
SELECT s.id, 'action_key', 'Action Name', ...
FROM service s;
```

### 5. Document Breaking Changes

```sql
-- ============================================
-- BREAKING CHANGE: This migration drops the old_table
-- Ensure all applications are updated before deployment
-- ============================================
DROP TABLE IF EXISTS area.old_table;
```

### 6. Test with Production Data

Before deploying, test migrations with production-like data:

```bash
# Create backup
pg_dump -h localhost -U area_user area_db > backup.sql

# Test migration
./gradlew flywayMigrate

# Verify results
psql -h localhost -U area_user area_db

# If issues, restore backup
psql -h localhost -U area_user area_db < backup.sql
```

## Common Patterns

### Pattern 1: Adding a New Service

```sql
-- 1. Insert service
INSERT INTO area.a_services (key, name, auth, is_active, docs_url, icon_light_url, icon_dark_url)
VALUES ('service_key', 'Service Name', 'OAUTH2', true, 'https://docs.url', 'icon_url', 'icon_url')
ON CONFLICT (key) DO UPDATE SET
    name = EXCLUDED.name,
    is_active = EXCLUDED.is_active;

-- 2. Add action definitions
WITH service AS (
    SELECT id FROM area.a_services WHERE key = 'service_key'
)
INSERT INTO area.a_action_definitions (service_id, key, name, description, ...)
SELECT s.id, 'action_key', 'Action Name', 'Description', ...
FROM service s
ON CONFLICT (service_id, key) DO NOTHING;
```

### Pattern 2: Modifying Column Type

```sql
-- Safe column type change
ALTER TABLE area.a_users 
ALTER COLUMN age TYPE integer USING age::integer;
```

### Pattern 3: Data Backfill

```sql
-- Backfill missing data
UPDATE area.a_users u
SET avatar_url = 'https://default-avatar.png'
WHERE avatar_url IS NULL;
```

### Pattern 4: Renaming Column

```sql
-- Rename column
ALTER TABLE area.a_users 
RENAME COLUMN old_name TO new_name;

-- Update dependent indexes
DROP INDEX IF EXISTS idx_old_name;
CREATE INDEX idx_new_name ON area.a_users(new_name);
```

## Rollback Strategy

### Flyway Limitations

**Important**: Flyway Community Edition does not support automatic rollback.

### Manual Rollback

Create a separate "undo" migration:

```sql
-- V12__Add_discord_service.sql (forward)
INSERT INTO area.a_services VALUES (...);

-- V13__Rollback_discord_service.sql (rollback)
DELETE FROM area.a_services WHERE key = 'discord';
```

### Best Rollback Practices

1. **Test Migrations**: Always test in dev/staging first
2. **Backup Database**: Create backup before major migrations
3. **Small Changes**: Make incremental, reversible changes
4. **Feature Flags**: Use application-level flags for new features
5. **Blue-Green Deployment**: Deploy to separate environment first

## Testing Migrations

### Local Testing

```bash
# 1. Clean database
./gradlew flywayClean  # ⚠️ Destroys all data

# 2. Run migrations
./gradlew flywayMigrate

# 3. Verify
./gradlew bootRun
```

### Integration Tests

```java
@SpringBootTest
@Testcontainers
class MigrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("test_db")
        .withUsername("test_user")
        .withPassword("test_pass");
    
    @Autowired
    private DataSource dataSource;
    
    @Test
    void testMigrationsApplySuccessfully() {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .schemas("area")
            .locations("classpath:db/migration")
            .load();
        
        MigrateResult result = flyway.migrate();
        assertTrue(result.success);
        assertTrue(result.migrationsExecuted >= 11);
    }
    
    @Test
    void testDiscordServiceExists() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM area.a_services WHERE key = 'discord'",
            Integer.class
        );
        assertEquals(1, count);
    }
}
```

## Troubleshooting

### Common Issues

#### 1. Checksum Mismatch

**Error**: `Migration checksum mismatch`

**Cause**: Migration file was modified after execution

**Solution**:
```bash
# Option 1: Repair (if change was unintentional)
./gradlew flywayRepair

# Option 2: Create new migration (if change was intentional)
# Don't modify existing migrations!
```

#### 2. Migration Failed

**Error**: `Migration V12__... failed`

**Solution**:
```bash
# 1. Check error in logs
tail -f logs/application.log

# 2. Fix the SQL in the migration file

# 3. Delete failed migration record
DELETE FROM area.flyway_schema_history WHERE version = '12';

# 4. Re-run migration
./gradlew flywayMigrate
```

#### 3. Out of Order Migration

**Error**: `Detected resolved migration not applied to database`

**Solution**:
```yaml
# Allow out-of-order migrations (not recommended for production)
spring:
  flyway:
    out-of-order: true
```

### Useful Flyway Commands

```bash
# View migration status
./gradlew flywayInfo

# Validate migrations
./gradlew flywayValidate

# Repair metadata table
./gradlew flywayRepair

# Clean database (⚠️ DESTRUCTIVE)
./gradlew flywayClean

# Run migrations
./gradlew flywayMigrate

# Generate baseline
./gradlew flywayBaseline
```

## Resources

- **Flyway Documentation**: https://flywaydb.org/documentation/
- **Migration Best Practices**: https://flywaydb.org/documentation/concepts/migrations
- **PostgreSQL Documentation**: https://www.postgresql.org/docs/
- **AREA Database Schema**: `docs/technical/05-database-schema.md`

## Checklist for New Migrations

- [ ] Increment version number correctly
- [ ] Use descriptive migration name
- [ ] Add header comment with description
- [ ] Write idempotent SQL
- [ ] Handle existing data appropriately
- [ ] Use `IF NOT EXISTS` / `ON CONFLICT` clauses
- [ ] Test migration locally
- [ ] Verify migration in `flyway_schema_history`
- [ ] Check data integrity after migration
- [ ] Document breaking changes if any
- [ ] Commit migration file to version control
- [ ] Test in staging environment before production
