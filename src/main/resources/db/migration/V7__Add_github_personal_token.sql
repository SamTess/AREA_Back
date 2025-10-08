-- V8: Add Personal Access Token support
-- This migration adds support for Personal Access Tokens (Classic)
-- for users who want to use their own tokens instead of OAuth

-- Add a new table for user personal tokens
CREATE TABLE IF NOT EXISTS area.a_user_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES area.a_users(id) ON DELETE CASCADE,
    token_name VARCHAR(255) NOT NULL DEFAULT 'Personal Access Token',
    token_enc TEXT NOT NULL, -- Encrypted token
    scopes JSONB, -- Token scopes like ["repo", "issues"]
    expires_at TIMESTAMP WITH TIME ZONE, -- Token expiration if known
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    -- Ensure one active token per user
    CONSTRAINT unique_active_github_token_per_user
        EXCLUDE (user_id WITH =) WHERE (is_active = true)
);

-- Create indexes
CREATE INDEX idx_user_tokens_user_id ON area.a_user_tokens(user_id);
CREATE INDEX idx_user_tokens_active ON area.a_user_tokens(user_id, is_active) WHERE is_active = true;

-- Add trigger for updated_at
CREATE TRIGGER trg_user_tokens_updated_at
    BEFORE UPDATE ON area.a_user_tokens
    FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();

-- Add a column to service accounts to reference personal tokens
ALTER TABLE area.a_service_accounts
ADD COLUMN IF NOT EXISTS personal_token_id UUID REFERENCES area.a_user_tokens(id) ON DELETE SET NULL;

-- Create index for the new column
CREATE INDEX IF NOT EXISTS idx_service_accounts_personal_token ON area.a_service_accounts(personal_token_id);


-- Add some helpful comments
COMMENT ON TABLE area.a_user_tokens IS 'Stores Personal Access Tokens for users';
COMMENT ON COLUMN area.a_user_tokens.token_enc IS 'Encrypted Personal Access Token';
COMMENT ON COLUMN area.a_user_tokens.scopes IS 'JSON array of token scopes like ["repo", "issues"]';
COMMENT ON COLUMN area.a_service_accounts.personal_token_id IS 'Reference to user personal token';