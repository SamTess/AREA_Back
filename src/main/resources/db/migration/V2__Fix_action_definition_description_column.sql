-- Migration to fix the description column type in a_action_definitions
-- Drop the views that depend on the description column

SET search_path TO area, public;

-- Drop the views temporarily
DROP VIEW IF EXISTS about_actions;
DROP VIEW IF EXISTS about_reactions;

-- Now we can alter the column type
ALTER TABLE a_action_definitions 
    ALTER COLUMN description SET DATA TYPE varchar(255);

-- Recreate the views with the same definitions
CREATE OR REPLACE VIEW about_actions AS
SELECT
  s.key                   AS service_key,
  s.name                  AS service_name,
  ad.key                  AS action_key,
  ad.name                 AS action_name,
  ad.description,
  ad.input_schema,
  ad.output_schema,
  ad.version,
  ad.docs_url
FROM a_action_definitions ad
JOIN a_services s ON s.id = ad.service_id
WHERE ad.is_event_capable = true
ORDER BY s.key, ad.key, ad.version DESC;

CREATE OR REPLACE VIEW about_reactions AS
SELECT
  s.key                   AS service_key,
  s.name                  AS service_name,
  ad.key                  AS reaction_key,
  ad.name                 AS reaction_name,
  ad.description,
  ad.input_schema,
  ad.output_schema,
  ad.version,
  ad.docs_url
FROM a_action_definitions ad
JOIN a_services s ON s.id = ad.service_id
WHERE ad.is_executable = true
ORDER BY s.key, ad.key, ad.version DESC;