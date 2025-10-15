-- Add firstname and lastname columns to a_users table

ALTER TABLE area.a_users
    ADD COLUMN IF NOT EXISTS firstname VARCHAR(100),
    ADD COLUMN IF NOT EXISTS lastname VARCHAR(100);

-- Add comments for documentation
COMMENT ON COLUMN area.a_users.firstname IS 'User first name';
COMMENT ON COLUMN area.a_users.lastname IS 'User last name';
