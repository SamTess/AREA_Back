-- ============================================
-- AREA - Migration V5: Remove redundant password fields
-- PostgreSQL 13+
-- ============================================
-- Remove salt column from a_user_local_identities (not needed with BCrypt)
-- Remove password_hash column from a_users (already stored in a_user_local_identities)
-- ============================================

-- Set schema context
SET search_path TO area, public;

-- Remove salt column from a_user_local_identities
-- BCrypt includes the salt in the hash itself, so separate salt storage is redundant
ALTER TABLE a_user_local_identities DROP COLUMN IF EXISTS salt;

-- Remove password_hash column from a_users
-- Password is properly stored in a_user_local_identities table with BCrypt encoding
ALTER TABLE a_users DROP COLUMN IF EXISTS password_hash;

-- Add comment to document the change
COMMENT ON TABLE a_user_local_identities IS 'Local authentication identities - password_hash uses BCrypt which includes salt automatically';
COMMENT ON COLUMN a_user_local_identities.password_hash IS 'BCrypt hash of the password (includes salt automatically)';
COMMENT ON TABLE a_users IS 'User accounts - password authentication handled via a_user_local_identities table';