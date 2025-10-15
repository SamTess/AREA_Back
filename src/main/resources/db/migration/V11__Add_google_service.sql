-- ============================================
-- AREA - Google Service
-- Add Google service with icons
-- ============================================

-- Add Google service
INSERT INTO area.a_services (key, name, auth, is_active, docs_url, icon_light_url, icon_dark_url) 
VALUES ('google', 'Google', 'OAUTH2', true, 'https://developers.google.com/apis-explorer', 
        'https://img.icons8.com/?size=100&id=17949&format=png&color=000000', 
        'https://img.icons8.com/?size=100&id=17949&format=png&color=000000')
ON CONFLICT (key) DO UPDATE SET
    name = EXCLUDED.name,
    auth = EXCLUDED.auth,
    is_active = EXCLUDED.is_active,
    docs_url = EXCLUDED.docs_url,
    icon_light_url = EXCLUDED.icon_light_url,
    icon_dark_url = EXCLUDED.icon_dark_url;
