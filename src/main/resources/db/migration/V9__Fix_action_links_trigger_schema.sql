-- Migration to fix the enforce_link_area_match function that references wrong schema
-- The function uses a_action_instances instead of area.a_action_instances

-- Drop and recreate the function with correct schema references
DROP FUNCTION IF EXISTS area.enforce_link_area_match() CASCADE;

CREATE OR REPLACE FUNCTION area.enforce_link_area_match()
RETURNS TRIGGER AS $$
DECLARE
  src_area uuid;
  dst_area uuid;
BEGIN
  SELECT area_id INTO src_area FROM area.a_action_instances WHERE id = NEW.source_action_instance_id;
  SELECT area_id INTO dst_area FROM area.a_action_instances WHERE id = NEW.target_action_instance_id;

  IF src_area IS NULL OR dst_area IS NULL THEN
    RAISE EXCEPTION 'action_links: source/target area_id not found';
  END IF;

  IF src_area <> dst_area THEN
    RAISE EXCEPTION 'action_links: source and target must belong to the same area (%, %)', src_area, dst_area;
  END IF;

  IF NEW.area_id <> src_area THEN
    RAISE EXCEPTION 'action_links: area_id must match the area of the linked action instances (%)', src_area;
  END IF;

  RETURN NEW;
END
$$ LANGUAGE plpgsql;

-- Recreate the trigger
DROP TRIGGER IF EXISTS trigger_enforce_link_area_match ON area.a_action_links;

CREATE TRIGGER trigger_enforce_link_area_match
    BEFORE INSERT OR UPDATE ON area.a_action_links
    FOR EACH ROW
    EXECUTE FUNCTION area.enforce_link_area_match();