# Data Migration Guide

This guide explains how to perform database migrations in the AREA Backend project using Flyway.

## Table of Contents

- [Overview](#overview)
- [Migration System](#migration-system)
- [Migration File Structure](#migration-file-structure)
- [Creating Migrations](#creating-migrations)
- [Migration Best Practices](#migration-best-practices)
- [Advanced Migration Techniques](#advanced-migration-techniques)
- [Testing Migrations](#testing-migrations)
- [Rollback Strategies](#rollback-strategies)
- [Production Deployment](#production-deployment)
- [Troubleshooting](#troubleshooting)

## Overview

The AREA Backend project uses Flyway for database schema versioning and migration management. Flyway automatically applies database changes in a controlled, versioned manner.

### Key Features

- **Version Control**: Database schema changes are versioned and tracked
- **Automatic Execution**: Migrations run automatically on application startup
- **Consistency**: Ensures all environments have the same database structure
- **Safety**: Prevents accidental overwrites and maintains migration history

## Migration System

### Flyway Configuration

Flyway is configured in the Spring Boot application with these settings:

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
    out-of-order: false
```

### Dependencies

```gradle
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
```

### Migration Location

All migration files are stored in:
```
src/main/resources/db/migration/
```

## Migration File Structure

### Naming Convention

Migration files must follow this pattern:
```
V{version}__{description}.sql
```

**Examples:**
- `V1__Initial_schema.sql`
- `V2__Add_user_email_index.sql`
- `V3__Create_area_table.sql`
- `V4__Add_service_auth_fields.sql`

### Version Numbers

- **Major versions**: `V1`, `V2`, `V3` for significant changes
- **Minor versions**: `V1.1`, `V1.2` for small updates
- **Patch versions**: `V1.1.1`, `V1.1.2` for hotfixes

### File Format

```sql
-- ============================================
-- Migration: V2__Add_user_email_index
-- Description: Add index on user email for performance
-- Author: Developer Name
-- Date: 2025-01-15
-- ============================================

-- Add index for faster email lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email 
ON users(email);

-- Add constraint for email uniqueness
ALTER TABLE users 
ADD CONSTRAINT uk_users_email 
UNIQUE (email);
```

## Creating Migrations

### Step 1: Analyze Requirements

Before creating a migration, consider:
- What database changes are needed?
- Will this affect existing data?
- Are there dependencies on other changes?
- How will this affect application code?

### Step 2: Create Migration File

1. **Determine version number:**
   ```bash
   # Check existing migrations
   ls src/main/resources/db/migration/
   # V1__Initial_schema.sql
   # V2__Fix_action_definition_description_column.sql
   
   # Next version would be V3
   ```

2. **Create new migration file:**
   ```bash
   touch src/main/resources/db/migration/V3__Add_user_preferences_table.sql
   ```

### Step 3: Write Migration SQL

```sql
-- ============================================
-- Migration: V3__Add_user_preferences_table
-- Description: Add user preferences table for customizable settings
-- ============================================

-- Create user_preferences table
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    preference_key VARCHAR(100) NOT NULL,
    preference_value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraint
    CONSTRAINT fk_user_preferences_user_id 
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    
    -- Unique constraint for user-preference combination
    CONSTRAINT uk_user_preferences_user_key 
        UNIQUE (user_id, preference_key)
);

-- Create indexes for performance
CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
CREATE INDEX idx_user_preferences_key ON user_preferences(preference_key);

-- Insert default preferences for existing users
INSERT INTO user_preferences (user_id, preference_key, preference_value)
SELECT id, 'theme', 'light'
FROM users
WHERE id NOT IN (
    SELECT DISTINCT user_id 
    FROM user_preferences 
    WHERE preference_key = 'theme'
);
```

## Migration Best Practices

### 1. Atomic Changes

Each migration should contain related changes only:

```sql
-- GOOD - Single focused change
-- V4__Add_service_authentication.sql
ALTER TABLE services ADD COLUMN auth_type VARCHAR(50);
ALTER TABLE services ADD COLUMN auth_config JSONB;
CREATE INDEX idx_services_auth_type ON services(auth_type);

-- BAD - Multiple unrelated changes
-- V4__Various_changes.sql
ALTER TABLE services ADD COLUMN auth_type VARCHAR(50);
CREATE TABLE notifications (...);
DROP INDEX old_unused_index;
```

### 2. Backwards Compatibility

Avoid breaking changes when possible:

```sql
-- GOOD - Additive change
ALTER TABLE users ADD COLUMN phone_number VARCHAR(20);

-- RISKY - Removing column (can break application)
ALTER TABLE users DROP COLUMN old_field;

-- BETTER - Deprecate first, remove later
-- V5__Deprecate_old_field.sql
ALTER TABLE users ADD COLUMN old_field_deprecated BOOLEAN DEFAULT TRUE;

-- V6__Remove_deprecated_field.sql (in future release)
ALTER TABLE users DROP COLUMN old_field;
```

### 3. Data Safety

Always backup and handle data carefully:

```sql
-- Create backup before major changes
CREATE TABLE users_backup AS SELECT * FROM users;

-- Modify data safely
UPDATE users 
SET email = LOWER(email) 
WHERE email != LOWER(email);

-- Verify changes
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM users WHERE email ~ '[A-Z]') > 0 THEN
        RAISE EXCEPTION 'Migration failed: uppercase emails still exist';
    END IF;
END $$;
```

### 4. Performance Considerations

Use non-blocking operations when possible:

```sql
-- GOOD - Non-blocking index creation
CREATE INDEX CONCURRENTLY idx_users_created_at ON users(created_at);

-- RISKY - Blocking operation on large table
CREATE INDEX idx_users_created_at ON users(created_at);

-- Add columns with defaults efficiently
ALTER TABLE users ADD COLUMN status VARCHAR(20) DEFAULT 'active';
-- For large tables, consider:
-- 1. Add column without default
-- 2. Update in batches
-- 3. Set default for new rows
```

### 5. Error Handling

Include validation and error handling:

```sql
-- Validate preconditions
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'users') THEN
        RAISE EXCEPTION 'Users table does not exist';
    END IF;
END $$;

-- Perform migration with error handling
DO $$
BEGIN
    -- Migration logic here
    
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Migration failed: %', SQLERRM;
        RAISE;
END $$;
```

## Advanced Migration Techniques

### 1. Data Transformation

```sql
-- V7__Normalize_user_data.sql

-- Create normalized tables
CREATE TABLE user_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    CONSTRAINT fk_user_addresses_user_id 
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Migrate existing address data
INSERT INTO user_addresses (user_id, address_line1, city, postal_code, country)
SELECT 
    id,
    address,
    city,
    postal_code,
    country
FROM users 
WHERE address IS NOT NULL;

-- Remove old columns (in separate migration)
-- ALTER TABLE users DROP COLUMN address;
-- ALTER TABLE users DROP COLUMN city;
-- ALTER TABLE users DROP COLUMN postal_code;
-- ALTER TABLE users DROP COLUMN country;
```

### 2. Complex Schema Changes

```sql
-- V8__Refactor_service_areas_relationship.sql

-- Create junction table for many-to-many relationship
CREATE TABLE service_area_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL,
    area_id UUID NOT NULL,
    mapping_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_service_area_mappings_service_id 
        FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE,
    CONSTRAINT fk_service_area_mappings_area_id 
        FOREIGN KEY (area_id) REFERENCES areas(id) ON DELETE CASCADE,
    CONSTRAINT uk_service_area_mappings 
        UNIQUE (service_id, area_id, mapping_type)
);

-- Migrate existing relationships
INSERT INTO service_area_mappings (service_id, area_id, mapping_type)
SELECT 
    s.id,
    a.id,
    'trigger'
FROM services s
JOIN areas a ON a.trigger_service_id = s.id;

INSERT INTO service_area_mappings (service_id, area_id, mapping_type)
SELECT 
    s.id,
    a.id,
    'action'
FROM services s
JOIN areas a ON a.action_service_id = s.id;
```

### 3. Conditional Migrations

```sql
-- V9__Add_feature_flags_if_not_exists.sql

-- Check if feature already exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'users' AND column_name = 'feature_flags'
    ) THEN
        ALTER TABLE users ADD COLUMN feature_flags JSONB DEFAULT '{}';
        CREATE INDEX idx_users_feature_flags ON users USING gin(feature_flags);
    END IF;
END $$;
```

## Testing Migrations

### 1. Local Testing

```bash
# Reset database to clean state
docker-compose down
docker-compose up -d postgres

# Run application to apply migrations
./gradlew bootRun

# Verify migration status
# Check application logs for Flyway output
```

### 2. Migration Testing Framework

```java
// Test class for migration verification
@SpringBootTest
@Testcontainers
class MigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Test
    void shouldApplyAllMigrationsSuccessfully() {
        // Migration is applied automatically by Spring Boot
        // Verify database state
        
        // Check table exists
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'users'", 
            Integer.class)).isEqualTo(1);
            
        // Check specific columns exist
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'email'", 
            Integer.class)).isEqualTo(1);
    }
}
```

### 3. Data Migration Testing

```sql
-- Test data for migration verification
-- test-data.sql

INSERT INTO users (id, email, username, password_hash) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'test1@example.com', 'user1', 'hash1'),
    ('550e8400-e29b-41d4-a716-446655440002', 'test2@example.com', 'user2', 'hash2');

-- Verify migration results
SELECT * FROM user_preferences WHERE user_id = '550e8400-e29b-41d4-a716-446655440001';
```

## Rollback Strategies

### 1. Preventive Measures

```sql
-- Always create backups before destructive operations
CREATE TABLE users_backup_v3 AS SELECT * FROM users;

-- Document rollback procedures in migration comments
-- ROLLBACK PROCEDURE:
-- 1. Stop application
-- 2. RESTORE FROM users_backup_v3
-- 3. DELETE FROM flyway_schema_history WHERE version = '3'
-- 4. Restart application with previous version
```

### 2. Compensating Migrations

Since Flyway doesn't support automatic rollbacks, create compensating migrations:

```sql
-- V10__Remove_user_preferences_table.sql (rollback for V3)

-- Remove foreign key constraints first
ALTER TABLE user_preferences DROP CONSTRAINT fk_user_preferences_user_id;

-- Drop indexes
DROP INDEX IF EXISTS idx_user_preferences_user_id;
DROP INDEX IF EXISTS idx_user_preferences_key;

-- Drop table
DROP TABLE IF EXISTS user_preferences;
```

### 3. Feature Flags for Rollback

Use feature flags to safely rollback functionality:

```sql
-- V11__Add_feature_toggle_support.sql

ALTER TABLE users ADD COLUMN feature_flags JSONB DEFAULT '{"new_preferences_ui": false}';

-- Application can check feature flags before using new functionality
-- SELECT feature_flags->>'new_preferences_ui' FROM users WHERE id = ?;
```

## Production Deployment

### 1. Pre-deployment Checklist

- [ ] Test migration on production-like data volume
- [ ] Verify migration is idempotent
- [ ] Create database backup
- [ ] Estimate migration execution time
- [ ] Plan rollback strategy
- [ ] Notify stakeholders of potential downtime

### 2. Deployment Process

```bash
# 1. Create backup
pg_dump -h localhost -U area_user area_db > backup_$(date +%Y%m%d_%H%M%S).sql

# 2. Deploy application (migrations run automatically)
# Monitor logs for migration progress

# 3. Verify deployment
curl -f http://localhost:8080/actuator/health

# 4. Check migration status in database
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

### 3. Zero-Downtime Migrations

For large tables, consider multi-step migrations:

```sql
-- Step 1: V12__Add_new_column_nullable.sql
ALTER TABLE users ADD COLUMN new_field VARCHAR(100);

-- Step 2: Deploy application code that can handle both old and new schema

-- Step 3: V13__Populate_new_column.sql
UPDATE users SET new_field = old_field WHERE new_field IS NULL;

-- Step 4: V14__Make_new_column_required.sql
ALTER TABLE users ALTER COLUMN new_field SET NOT NULL;

-- Step 5: Deploy application code that only uses new column

-- Step 6: V15__Remove_old_column.sql
ALTER TABLE users DROP COLUMN old_field;
```

## Troubleshooting

### Common Issues

1. **Migration Checksum Mismatch**
   ```
   ERROR: Migration checksum mismatch for migration version 2
   ```
   
   **Solution:**
   ```sql
   -- Fix checksum in flyway_schema_history
   UPDATE flyway_schema_history 
   SET checksum = <new_checksum> 
   WHERE version = '2';
   ```

2. **Failed Migration**
   ```
   ERROR: Migration V3__Add_user_preferences_table.sql failed
   ```
   
   **Solution:**
   ```sql
   -- Check failed migration status
   SELECT * FROM flyway_schema_history WHERE success = false;
   
   -- Clean up partial changes
   -- (Run cleanup SQL specific to your migration)
   
   -- Remove failed migration record
   DELETE FROM flyway_schema_history WHERE version = '3';
   
   -- Fix migration file and retry
   ```

3. **Out of Order Migration**
   ```
   ERROR: Detected applied migration not resolved locally
   ```
   
   **Solution:**
   ```yaml
   # Allow out-of-order migrations (use carefully)
   spring:
     flyway:
       out-of-order: true
   ```

### Best Practices for Troubleshooting

1. **Monitor Migration Logs**
   ```bash
   # Watch application logs during startup
   tail -f logs/application.log | grep -i flyway
   ```

2. **Database State Verification**
   ```sql
   -- Check current schema version
   SELECT version, description, installed_on, success 
   FROM flyway_schema_history 
   ORDER BY installed_rank DESC;
   
   -- Verify table structure
   \d+ table_name  -- PostgreSQL
   ```

3. **Backup Strategy**
   ```bash
   # Automated backup before migration
   #!/bin/bash
   backup_name="backup_$(date +%Y%m%d_%H%M%S).sql"
   pg_dump -h $DB_HOST -U $DB_USER $DB_NAME > $backup_name
   echo "Backup created: $backup_name"
   ```

### Recovery Procedures

1. **Partial Migration Failure**
   - Stop application
   - Assess database state
   - Clean up partial changes
   - Fix migration script
   - Remove failed migration from history
   - Restart application

2. **Complete System Failure**
   - Restore from backup
   - Update flyway_schema_history to previous state
   - Deploy previous application version
   - Investigate and fix migration issues

This comprehensive guide should help you effectively manage database migrations in the AREA Backend project using Flyway.