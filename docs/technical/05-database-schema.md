# Database Schema Documentation

## Table of Contents
- [Overview](#overview)
- [Schema Design Principles](#schema-design-principles)
- [Database Configuration](#database-configuration)
- [Core Tables](#core-tables)
- [Security Tables](#security-tables)
- [Execution Tables](#execution-tables)
- [Indexes and Performance](#indexes-and-performance)
- [Migration Strategy](#migration-strategy)
- [Data Types](#data-types)

## Overview

The AREA backend uses PostgreSQL 13+ as the primary database with a schema designed for scalability, flexibility, and maintainability. The schema follows a modular approach with clear separation between user management, service integration, automation logic, and execution tracking.

## Schema Design Principles

### 1. Modularity
- Dedicated schema namespace (`area`)
- Logical grouping of related tables
- Clear table naming conventions (`a_` prefix)

### 2. Scalability
- UUID primary keys for horizontal scaling
- JSONB columns for flexible schema evolution
- Efficient indexing strategy
- Audit trails for all major entities

### 3. Data Integrity
- Foreign key constraints
- Check constraints for data validation
- NOT NULL constraints where appropriate
- Unique constraints for business rules

### 4. Security
- Encrypted sensitive data storage
- Audit trails with timestamps
- Soft delete patterns where needed

## Database Configuration

### Connection Configuration
```properties
# Database Configuration
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.format_sql=false

# Flyway Configuration
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

### Database Connection Pool
```yaml
# HikariCP Configuration (implicit with Spring Boot)
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 20000
```

## Core Tables

### Users Table (`a_users`)
```sql
CREATE TABLE area.a_users (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email            citext UNIQUE NOT NULL,
    is_active        boolean NOT NULL DEFAULT true,
    is_admin         boolean NOT NULL DEFAULT false,
    created_at       timestamptz NOT NULL DEFAULT now(),
    last_login_at    timestamptz,
    avatar_url       text
);

-- Indexes
CREATE INDEX idx_users_email ON area.a_users(email);
CREATE INDEX idx_users_active ON area.a_users(is_active);
CREATE INDEX idx_users_created_at ON area.a_users(created_at);
```

**Entity Mapping:**
```java
@Entity
@Table(name = "a_users", schema = "area")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Email
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_admin", nullable = false)
    private Boolean isAdmin = false;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "avatar_url")
    private String avatarUrl;
}
```

### Services Table (`a_services`)
```sql
CREATE TABLE area.a_services (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    key              text UNIQUE NOT NULL,
    name             text NOT NULL,
    auth             varchar(20) NOT NULL DEFAULT 'oauth2',
    docs_url         text,
    icon_light_url   text,
    icon_dark_url    text,
    is_active        boolean NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

-- Indexes
CREATE UNIQUE INDEX idx_services_key ON area.a_services(key);
CREATE INDEX idx_services_active ON area.a_services(is_active);

-- Trigger for updated_at
CREATE TRIGGER trg_services_updated_at 
    BEFORE UPDATE ON area.a_services
    FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();
```

### Areas Table (`a_areas`)
```sql
CREATE TABLE area.a_areas (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid NOT NULL REFERENCES area.a_users(id) ON DELETE CASCADE,
    name             text NOT NULL,
    description      text,
    enabled          boolean NOT NULL DEFAULT true,
    actions          jsonb,
    reactions        jsonb,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_areas_user_id ON area.a_areas(user_id);
CREATE INDEX idx_areas_enabled ON area.a_areas(enabled);
CREATE INDEX idx_areas_created_at ON area.a_areas(created_at);

-- JSONB GIN indexes for efficient querying
CREATE INDEX idx_areas_actions_gin ON area.a_areas USING gin(actions);
CREATE INDEX idx_areas_reactions_gin ON area.a_areas USING gin(reactions);

-- Trigger for updated_at
CREATE TRIGGER trg_areas_updated_at 
    BEFORE UPDATE ON area.a_areas
    FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();
```

### Action Definitions Table (`a_action_definitions`)
```sql
CREATE TABLE area.a_action_definitions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    key                 text UNIQUE NOT NULL,
    name                text NOT NULL,
    description         text,
    service_key         text NOT NULL REFERENCES area.a_services(key),
    is_event_capable    boolean NOT NULL DEFAULT false,
    is_executable       boolean NOT NULL DEFAULT false,
    input_schema        jsonb,
    output_schema       jsonb,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

-- Indexes
CREATE UNIQUE INDEX idx_action_definitions_key ON area.a_action_definitions(key);
CREATE INDEX idx_action_definitions_service ON area.a_action_definitions(service_key);
CREATE INDEX idx_action_definitions_event_capable ON area.a_action_definitions(is_event_capable);
CREATE INDEX idx_action_definitions_executable ON area.a_action_definitions(is_executable);

-- JSONB GIN indexes
CREATE INDEX idx_action_definitions_input_schema_gin ON area.a_action_definitions USING gin(input_schema);
CREATE INDEX idx_action_definitions_output_schema_gin ON area.a_action_definitions USING gin(output_schema);
```

### Action Instances Table (`a_action_instances`)
```sql
CREATE TABLE area.a_action_instances (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id                 uuid NOT NULL REFERENCES area.a_areas(id) ON DELETE CASCADE,
    action_definition_id    uuid NOT NULL REFERENCES area.a_action_definitions(id),
    service_account_id      uuid REFERENCES area.a_service_accounts(id),
    parameters              jsonb,
    activation_mode         varchar(20) NOT NULL DEFAULT 'WEBHOOK',
    cron_expression         text,
    enabled                 boolean NOT NULL DEFAULT true,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_action_instances_area_id ON area.a_action_instances(area_id);
CREATE INDEX idx_action_instances_action_def ON area.a_action_instances(action_definition_id);
CREATE INDEX idx_action_instances_service_account ON area.a_action_instances(service_account_id);
CREATE INDEX idx_action_instances_activation_mode ON area.a_action_instances(activation_mode);
CREATE INDEX idx_action_instances_enabled ON area.a_action_instances(enabled);

-- JSONB GIN index
CREATE INDEX idx_action_instances_parameters_gin ON area.a_action_instances USING gin(parameters);
```

## Security Tables

### User Local Identities (`a_user_local_identities`)
```sql
CREATE TABLE area.a_user_local_identities (
    id                              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                         uuid NOT NULL REFERENCES area.a_users(id) ON DELETE CASCADE,
    email                          citext UNIQUE NOT NULL,
    password_hash                  text NOT NULL,
    salt                           text,
    is_email_verified              boolean NOT NULL DEFAULT false,
    email_verification_token       text,
    email_verification_expires_at  timestamptz,
    password_reset_token           text,
    password_reset_expires_at      timestamptz,
    failed_login_attempts          integer NOT NULL DEFAULT 0,
    locked_until                   timestamptz,
    last_password_change_at        timestamptz,
    created_at                     timestamptz NOT NULL DEFAULT now(),
    updated_at                     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id)
);

-- Indexes
CREATE INDEX idx_user_local_identities_email ON area.a_user_local_identities(email);
CREATE INDEX idx_user_local_identities_verification_token ON area.a_user_local_identities(email_verification_token);
CREATE INDEX idx_user_local_identities_reset_token ON area.a_user_local_identities(password_reset_token);
```

### User OAuth Identities (`a_user_oauth_identities`)
```sql
CREATE TABLE area.a_user_oauth_identities (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL REFERENCES area.a_users(id) ON DELETE CASCADE,
    provider            text NOT NULL,
    provider_user_id    text NOT NULL,
    access_token_enc    text,
    refresh_token_enc   text,
    expires_at          timestamptz,
    scopes              jsonb,
    token_meta          jsonb,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, provider)
);

-- Indexes
CREATE INDEX idx_user_oauth_identities_user_id ON area.a_user_oauth_identities(user_id);
CREATE INDEX idx_user_oauth_identities_provider ON area.a_user_oauth_identities(provider);
CREATE UNIQUE INDEX idx_user_oauth_identities_provider_user ON area.a_user_oauth_identities(provider, provider_user_id);
```

### Service Accounts (`a_service_accounts`)
```sql
CREATE TABLE area.a_service_accounts (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              uuid NOT NULL REFERENCES area.a_users(id) ON DELETE CASCADE,
    service_id           uuid NOT NULL REFERENCES area.a_services(id),
    account_name         text NOT NULL,
    access_token_enc     text,
    refresh_token_enc    text,
    expires_at           timestamptz,
    scopes               text,
    metadata             jsonb,
    is_active            boolean NOT NULL DEFAULT true,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_service_accounts_user_id ON area.a_service_accounts(user_id);
CREATE INDEX idx_service_accounts_service_id ON area.a_service_accounts(service_id);
CREATE INDEX idx_service_accounts_active ON area.a_service_accounts(is_active);

-- JSONB GIN index
CREATE INDEX idx_service_accounts_metadata_gin ON area.a_service_accounts USING gin(metadata);
```

## Execution Tables

### Executions Table (`a_executions`)
```sql
CREATE TABLE area.a_executions (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id               uuid NOT NULL REFERENCES area.a_areas(id),
    trigger_action_id     uuid REFERENCES area.a_action_instances(id),
    input_data            jsonb,
    output_data           jsonb,
    status                varchar(20) NOT NULL DEFAULT 'QUEUED',
    error_message         text,
    retry_count           integer NOT NULL DEFAULT 0,
    triggered_at          timestamptz NOT NULL DEFAULT now(),
    started_at            timestamptz,
    completed_at          timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_executions_area_id ON area.a_executions(area_id);
CREATE INDEX idx_executions_trigger_action ON area.a_executions(trigger_action_id);
CREATE INDEX idx_executions_status ON area.a_executions(status);
CREATE INDEX idx_executions_triggered_at ON area.a_executions(triggered_at);
CREATE INDEX idx_executions_retry_count ON area.a_executions(retry_count);

-- JSONB GIN indexes
CREATE INDEX idx_executions_input_data_gin ON area.a_executions USING gin(input_data);
CREATE INDEX idx_executions_output_data_gin ON area.a_executions USING gin(output_data);

-- Composite indexes for common queries
CREATE INDEX idx_executions_status_triggered ON area.a_executions(status, triggered_at);
CREATE INDEX idx_executions_area_status ON area.a_executions(area_id, status);
```

### Activation Modes Table (`a_activation_modes`)
```sql
CREATE TABLE area.a_activation_modes (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id             uuid NOT NULL REFERENCES area.a_areas(id) ON DELETE CASCADE,
    mode_type           varchar(20) NOT NULL,
    configuration       jsonb,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_activation_modes_area_id ON area.a_activation_modes(area_id);
CREATE INDEX idx_activation_modes_type ON area.a_activation_modes(mode_type);
CREATE INDEX idx_activation_modes_active ON area.a_activation_modes(is_active);

-- JSONB GIN index
CREATE INDEX idx_activation_modes_config_gin ON area.a_activation_modes USING gin(configuration);
```

### Action Links Table (`a_action_links`)
```sql
CREATE TABLE area.a_action_links (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id                 uuid NOT NULL REFERENCES area.a_areas(id) ON DELETE CASCADE,
    source_action_id        uuid NOT NULL REFERENCES area.a_action_instances(id),
    target_reaction_id      uuid NOT NULL REFERENCES area.a_action_instances(id),
    data_mapping            jsonb,
    link_type               varchar(20) NOT NULL DEFAULT 'DIRECT',
    conditions              jsonb,
    is_active               boolean NOT NULL DEFAULT true,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_action_links_area_id ON area.a_action_links(area_id);
CREATE INDEX idx_action_links_source ON area.a_action_links(source_action_id);
CREATE INDEX idx_action_links_target ON area.a_action_links(target_reaction_id);
CREATE INDEX idx_action_links_type ON area.a_action_links(link_type);
CREATE INDEX idx_action_links_active ON area.a_action_links(is_active);

-- JSONB GIN indexes
CREATE INDEX idx_action_links_mapping_gin ON area.a_action_links USING gin(data_mapping);
CREATE INDEX idx_action_links_conditions_gin ON area.a_action_links USING gin(conditions);
```

## Indexes and Performance

### Primary Indexes
All tables use UUID primary keys with B-tree indexes for:
- Fast point lookups
- Efficient joins
- Distributed scaling support

### Foreign Key Indexes
All foreign key columns have dedicated indexes for:
- Join performance optimization
- Referential integrity checks
- Cascade operation efficiency

### JSONB Indexes
GIN (Generalized Inverted) indexes on JSONB columns for:
- Fast JSON property searches
- Containment operations
- Complex query optimization

### Composite Indexes
Strategic composite indexes for common query patterns:
```sql
-- Execution queries by area and status
CREATE INDEX idx_executions_area_status ON area.a_executions(area_id, status);

-- Time-based queries with status
CREATE INDEX idx_executions_status_triggered ON area.a_executions(status, triggered_at);

-- User areas with enabled status
CREATE INDEX idx_areas_user_enabled ON area.a_areas(user_id, enabled);
```

### Performance Optimization Strategies

#### Query Optimization
```sql
-- Efficient area lookup with actions
SELECT a.*, ai.* 
FROM area.a_areas a
LEFT JOIN area.a_action_instances ai ON a.id = ai.area_id
WHERE a.user_id = ? AND a.enabled = true
ORDER BY a.created_at DESC
LIMIT 20;

-- JSONB query optimization
SELECT * FROM area.a_areas 
WHERE actions @> '[{"action_definition_id": "github-issue-created"}]';
```

#### Connection Pool Tuning
```properties
# HikariCP optimizations
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000
spring.datasource.hikari.leak-detection-threshold=60000
```

## Migration Strategy

### Flyway Configuration
```sql
-- V1__Initial_schema.sql
-- Creates all base tables, indexes, and functions

-- V2__Fix_action_definition_description_column.sql
-- Schema corrections and improvements

-- V3__Change_all_enum_columns_to_varchar.sql
-- Enum to varchar migration for flexibility

-- V4__Fix_execution_area_trigger.sql
-- Trigger improvements

-- V5__Add_github_service_and_actions.sql
-- GitHub service integration

-- V6__Add_jsonb_actions_reactions_to_areas.sql
-- JSONB column additions for flexibility

-- V7__Add_github_personal_token.sql
-- Personal token support

-- V8__Add_link_type_to_action_links.sql
-- Link type enhancements

-- V9__Fix_action_links_trigger_schema.sql
-- Trigger fixes and improvements
```

### Migration Best Practices
1. **Incremental Changes**: Small, focused migrations
2. **Rollback Support**: Reversible operations where possible
3. **Data Preservation**: No destructive changes without backups
4. **Index Management**: Create indexes CONCURRENTLY in production
5. **Testing**: Validate migrations in staging environment

### Example Migration
```sql
-- V10__Add_user_preferences.sql
BEGIN;

-- Add new column with default value
ALTER TABLE area.a_users 
ADD COLUMN preferences jsonb DEFAULT '{}';

-- Create index for new column
CREATE INDEX CONCURRENTLY idx_users_preferences_gin 
ON area.a_users USING gin(preferences);

-- Update existing users with default preferences
UPDATE area.a_users 
SET preferences = '{
    "notifications": true,
    "theme": "light",
    "timezone": "UTC"
}'
WHERE preferences IS NULL;

COMMIT;
```

## Data Types

### UUID Usage
```sql
-- Primary keys
id uuid PRIMARY KEY DEFAULT gen_random_uuid()

-- Foreign keys
user_id uuid NOT NULL REFERENCES area.a_users(id)
```

**Benefits:**
- Globally unique identifiers
- Horizontal scaling support
- No collision risk across instances
- 128-bit entropy

### JSONB Usage
```sql
-- Flexible configuration storage
parameters jsonb
metadata jsonb
input_schema jsonb
output_schema jsonb
```

**Benefits:**
- Schema flexibility
- Efficient storage and indexing
- Native JSON operations
- GIN index support

### Timestamp Usage
```sql
-- All timestamps with timezone
created_at timestamptz NOT NULL DEFAULT now()
updated_at timestamptz NOT NULL DEFAULT now()
```

**Benefits:**
- Timezone awareness
- Consistent global timestamps
- Automatic UTC conversion

### Text vs VARCHAR
```sql
-- Use text for unlimited length
description text
error_message text

-- Use varchar for constrained values
status varchar(20) NOT NULL
activation_mode varchar(20) NOT NULL
```

## Database Functions

### Utility Functions
```sql
-- Updated timestamp function
CREATE OR REPLACE FUNCTION area.set_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := NOW();
  RETURN NEW;
END$$;

-- Payload hash function
CREATE OR REPLACE FUNCTION area.payload_sha256(p jsonb)
RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT encode(digest(convert_to(p::text, 'UTF8'), 'sha256'), 'hex')
$$;
```

### Triggers
```sql
-- Automatic updated_at management
CREATE TRIGGER trg_areas_updated_at 
    BEFORE UPDATE ON area.a_areas
    FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();

CREATE TRIGGER trg_service_accounts_updated_at 
    BEFORE UPDATE ON area.a_service_accounts
    FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();
```

## Monitoring and Maintenance

### Query Performance Monitoring
```sql
-- Enable query statistics
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Monitor slow queries
SELECT query, calls, total_time, mean_time, rows
FROM pg_stat_statements
WHERE mean_time > 100
ORDER BY mean_time DESC
LIMIT 20;
```

### Index Usage Analysis
```sql
-- Check index usage
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'area'
ORDER BY idx_scan DESC;
```

### Database Size Monitoring
```sql
-- Table sizes
SELECT schemaname, tablename,
       pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables 
WHERE schemaname = 'area'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```