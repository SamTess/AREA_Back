ALTER TABLE area.a_user_local_identities
ADD COLUMN IF NOT EXISTS is_oauth_placeholder boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN area.a_user_local_identities.is_oauth_placeholder IS 'True if this local identity was created as a placeholder for OAuth-only users';
