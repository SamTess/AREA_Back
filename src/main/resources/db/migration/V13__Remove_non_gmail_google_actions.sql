-- ============================================
-- AREA - Remove Non-Gmail Google Actions
-- Remove Drive, Calendar, and Sheets actions, keep only Gmail
-- ============================================

-- Delete non-Gmail Google action definitions
DELETE FROM area.a_action_definitions
WHERE service_id = (SELECT id FROM area.a_services WHERE key = 'google')
AND key NOT IN ('gmail_new_email', 'gmail_send_email', 'gmail_add_label');

-- Log the cleanup
DO $$
BEGIN
    RAISE NOTICE 'Removed non-Gmail Google actions. Kept only: gmail_new_email, gmail_send_email, gmail_add_label';
END $$;
