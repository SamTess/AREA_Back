-- Migration to change ALL enum columns to varchar for easier handling
-- This allows us to use simple string enums without PostgreSQL custom enum types

SET search_path TO area, public;

-- Change auth column from enum to varchar
ALTER TABLE a_services ALTER COLUMN auth TYPE varchar(20);

-- Change dedup column from enum to varchar
ALTER TABLE a_activation_modes ALTER COLUMN dedup TYPE varchar(50);

-- Change type column from enum to varchar
ALTER TABLE a_activation_modes ALTER COLUMN type TYPE varchar(20);

-- Change status column from enum to varchar
ALTER TABLE a_executions ALTER COLUMN status TYPE varchar(20);

-- Add check constraints to maintain data integrity (using uppercase to match Java enum names)
ALTER TABLE a_services ADD CONSTRAINT chk_auth_values
  CHECK (auth IN ('OAUTH2', 'APIKEY', 'NONE'));

ALTER TABLE a_activation_modes ADD CONSTRAINT chk_dedup_values
  CHECK (dedup IN ('NONE', 'BY_PAYLOAD_HASH', 'BY_EXTERNAL_ID'));

ALTER TABLE a_activation_modes ADD CONSTRAINT chk_type_values
  CHECK (type IN ('CRON', 'WEBHOOK', 'POLL', 'MANUAL', 'CHAIN'));

ALTER TABLE a_executions ADD CONSTRAINT chk_status_values
  CHECK (status IN ('QUEUED', 'RUNNING', 'OK', 'RETRY', 'FAILED', 'CANCELED'));