-- Add JSONB columns for actions and reactions to a_areas table
-- This migration adds support for storing actions and reactions as JSONB data

ALTER TABLE area.a_areas
ADD COLUMN IF NOT EXISTS actions jsonb,
ADD COLUMN IF NOT EXISTS reactions jsonb;

-- Add indexes for JSON querying performance
CREATE INDEX IF NOT EXISTS idx_areas_actions ON area.a_areas USING gin(actions);
CREATE INDEX IF NOT EXISTS idx_areas_reactions ON area.a_areas USING gin(reactions);

-- Add comments
COMMENT ON COLUMN area.a_areas.actions IS 'JSONB array of action configurations for this AREA';
COMMENT ON COLUMN area.a_areas.reactions IS 'JSONB array of reaction configurations for this AREA';