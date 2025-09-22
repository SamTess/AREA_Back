-- Initial schema for AREA application
-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Services table
CREATE TABLE IF NOT EXISTS services (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    icon_url VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    api_endpoint VARCHAR(255),
    auth_type VARCHAR(50) NOT NULL DEFAULT 'OAUTH2',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Areas table
CREATE TABLE IF NOT EXISTS areas (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    user_id BIGINT NOT NULL,
    action_service_id BIGINT NOT NULL,
    action_type VARCHAR(255) NOT NULL,
    action_config TEXT,
    reaction_service_id BIGINT NOT NULL,
    reaction_type VARCHAR(255) NOT NULL,
    reaction_config TEXT,
    last_triggered TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_areas_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_areas_action_service FOREIGN KEY (action_service_id) REFERENCES services(id) ON DELETE CASCADE,
    CONSTRAINT fk_areas_reaction_service FOREIGN KEY (reaction_service_id) REFERENCES services(id) ON DELETE CASCADE
);

-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_services_name ON services(name);
CREATE INDEX IF NOT EXISTS idx_areas_user_id ON areas(user_id);
CREATE INDEX IF NOT EXISTS idx_areas_action_service_id ON areas(action_service_id);
CREATE INDEX IF NOT EXISTS idx_areas_reaction_service_id ON areas(reaction_service_id);
CREATE INDEX IF NOT EXISTS idx_areas_enabled ON areas(enabled);

-- Insert some sample services
INSERT INTO services (name, description, icon_url, api_endpoint, auth_type) VALUES
('Gmail', 'Google Gmail service for email actions and reactions', 'https://ssl.gstatic.com/ui/v1/icons/mail/rfr/gmail.ico', 'https://gmail.googleapis.com', 'OAUTH2'),
('GitHub', 'GitHub service for repository actions and reactions', 'https://github.githubassets.com/favicons/favicon.svg', 'https://api.github.com', 'OAUTH2'),
('Slack', 'Slack service for team communication', 'https://slack.com/favicon.ico', 'https://slack.com/api', 'OAUTH2'),
('Discord', 'Discord service for community communication', 'https://discord.com/assets/favicon.ico', 'https://discord.com/api', 'OAUTH2'),
('Weather API', 'Weather information service', 'https://openweathermap.org/favicon.ico', 'https://api.openweathermap.org', 'API_KEY')
ON CONFLICT (name) DO NOTHING;