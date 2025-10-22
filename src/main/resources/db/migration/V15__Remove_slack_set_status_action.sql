-- Remove Slack set_status action (requires users.profile:write scope which can't be obtained via user OAuth)

DELETE FROM area.a_action_definitions
WHERE service_id = (SELECT id FROM area.a_services WHERE key = 'slack')
  AND key = 'set_status';
