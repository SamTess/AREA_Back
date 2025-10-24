-- Add username column initially as nullable to allow data migration
ALTER TABLE area.a_users ADD COLUMN username VARCHAR(50) UNIQUE;

-- Generate unique usernames for existing users without one
-- Format: user_{uuid_first_8_chars}
UPDATE area.a_users 
SET username = CONCAT('user_', SUBSTRING(CAST(id AS VARCHAR), 1, 8))
WHERE username IS NULL;

-- Now make the column NOT NULL after data migration
ALTER TABLE area.a_users ALTER COLUMN username SET NOT NULL;
