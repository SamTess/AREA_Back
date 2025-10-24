-- Remove search_pages action (returns data like a trigger, not an action)
DELETE FROM area.a_action_definitions
WHERE key = 'search_pages'
  AND service_id = (SELECT id FROM area.a_services WHERE key = 'notion');

-- Remove new_database_item trigger (API version doesn't support it)
DELETE FROM area.a_action_definitions
WHERE key = 'new_database_item'
  AND service_id = (SELECT id FROM area.a_services WHERE key = 'notion');
