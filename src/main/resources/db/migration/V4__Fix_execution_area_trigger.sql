-- Migration to fix the set_execution_area trigger to handle missing records gracefully
-- This prevents the trigger from failing when a_action_instances record is not found

SET search_path TO area, public;

-- Replace the trigger function to handle missing records gracefully
CREATE OR REPLACE FUNCTION area.set_execution_area()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  ai_area uuid;
BEGIN
  -- Only set area_id if it's not already set and the action_instance exists
  IF NEW.area_id IS NULL THEN
    SELECT area_id INTO ai_area FROM a_action_instances WHERE id = NEW.action_instance_id;
    IF FOUND THEN
      NEW.area_id := ai_area;
    END IF;
  END IF;
  RETURN NEW;
END$$;