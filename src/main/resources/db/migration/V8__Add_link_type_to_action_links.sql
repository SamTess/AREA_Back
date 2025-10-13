-- Migration to add link_type column to action_links table
-- This allows support for different types of connections (chain, conditional, parallel, sequential)

ALTER TABLE area.a_action_links 
ADD COLUMN link_type VARCHAR(50) NOT NULL DEFAULT 'chain';

-- Add a check constraint to ensure only valid link types are allowed
ALTER TABLE area.a_action_links 
ADD CONSTRAINT chk_link_type 
CHECK (link_type IN ('chain', 'conditional', 'parallel', 'sequential'));

-- Add an index on link_type for better query performance
CREATE INDEX idx_action_links_link_type ON area.a_action_links(link_type);

-- Add comments for documentation
COMMENT ON COLUMN area.a_action_links.link_type IS 'Type of link between actions: chain (sequential execution), conditional (based on condition), parallel (simultaneous execution), sequential (ordered execution)';