-- ============================================
-- AREA - Unified schema (Actions + Areas)
-- PostgreSQL 13+
-- =======================================-- (Optional) Sharing/collab=
-- Useful extensions
-- =========================
CREATE EXTENSION IF NOT EXISTS pgcrypto;      -- gen_random_uuid(), digest()
CREATE EXTENSION IF NOT EXISTS citext;        -- case-insensitive emails (optional)

-- Dedicated schema (optional)
CREATE SCHEMA IF NOT EXISTS area;
SET search_path TO area, public;

-- =========================
-- Enumerated types
-- =========================
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'activation_mode_type') THEN
    CREATE TYPE activation_mode_type AS ENUM ('CRON','WEBHOOK','POLL','MANUAL','CHAIN');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'execution_status') THEN
    CREATE TYPE execution_status AS ENUM ('QUEUED','RUNNING','OK','RETRY','FAILED','CANCELED');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'dedup_strategy') THEN
    CREATE TYPE dedup_strategy AS ENUM ('none','by_payload_hash','by_external_id');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'auth_type') THEN
    CREATE TYPE auth_type AS ENUM ('oauth2','apikey','none');
  END IF;
END$$;

-- =========================
-- Common functions / triggers
-- =========================
CREATE OR REPLACE FUNCTION area.set_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := NOW();
  RETURN NEW;
END$$;

CREATE OR REPLACE FUNCTION area.payload_sha256(p jsonb)
RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT encode(digest(convert_to(p::text, 'UTF8'), 'sha256'), 'hex')
$$;

-- =========================
-- Users & Identity
-- =========================
CREATE TABLE IF NOT EXISTS a_users (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email            citext UNIQUE NOT NULL,
  password_hash    text,                           -- nullable if account is OAuth-only
  is_active        boolean NOT NULL DEFAULT true,
  is_admin         boolean NOT NULL DEFAULT false,
  created_at       timestamptz NOT NULL DEFAULT now(),
  confirmed_at     timestamptz,
  last_login_at    timestamptz,
  avatar_url       text
);

CREATE TABLE IF NOT EXISTS a_user_oauth_identities (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE,
  provider         text NOT NULL,                  -- e.g.: google, github
  provider_user_id text NOT NULL,
  access_token_enc text,
  refresh_token_enc text,
  expires_at       timestamptz,
  scopes           jsonb,                          -- human-readable
  token_meta       jsonb,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, provider)
);
CREATE TRIGGER trg_uoi_updated_at BEFORE UPDATE ON a_user_oauth_identities
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();

-- Table for local identities (email/password login)
CREATE TABLE IF NOT EXISTS a_user_local_identities (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE,
  email            citext UNIQUE NOT NULL,         -- login email (may differ from users.email)
  password_hash    text NOT NULL,                  -- bcrypt hash of the password
  salt             text,                           -- additional salt if needed
  is_email_verified boolean NOT NULL DEFAULT false,
  email_verification_token text,                   -- token for email verification
  email_verification_expires_at timestamptz,      -- token expiration
  password_reset_token text,                       -- token for password reset
  password_reset_expires_at timestamptz,          -- password reset token expiration
  failed_login_attempts integer NOT NULL DEFAULT 0,
  locked_until     timestamptz,                    -- temporary lockout after failures
  last_password_change_at timestamptz,            -- last password change timestamp
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id)                                 -- single local account per user
);
CREATE INDEX IF NOT EXISTS idx_user_local_identities_email ON a_user_local_identities(email);
CREATE INDEX IF NOT EXISTS idx_user_local_identities_verification ON a_user_local_identities(email_verification_token) WHERE email_verification_token IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_user_local_identities_reset ON a_user_local_identities(password_reset_token) WHERE password_reset_token IS NOT NULL;
CREATE TRIGGER trg_uli_updated_at BEFORE UPDATE ON a_user_local_identities
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();

-- =========================
-- Services catalog
-- =========================
CREATE TABLE IF NOT EXISTS a_services (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  key              text UNIQUE NOT NULL,           -- e.g.: "gmail", "github"
  name             text NOT NULL,
  auth             auth_type NOT NULL DEFAULT 'oauth2',
  docs_url         text,
  icon_light_url   text,
  icon_dark_url    text,
  is_active        boolean NOT NULL DEFAULT true,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_services_updated_at BEFORE UPDATE ON a_services
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();

-- Accounts linked to a service (tokens, secrets)
CREATE TABLE IF NOT EXISTS a_service_accounts (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id            uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE,
  service_id         uuid NOT NULL REFERENCES a_services(id) ON DELETE CASCADE,
  remote_account_id  text,                          -- service-side account id (optional)
  access_token_enc   text,
  refresh_token_enc  text,
  expires_at         timestamptz,
  scopes             jsonb,
  webhook_secret_enc text,                          -- for signature verification
  token_version      integer NOT NULL DEFAULT 1,
  last_refresh_at    timestamptz,
  revoked_at         timestamptz,
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, service_id)
);
CREATE INDEX IF NOT EXISTS idx_service_accounts_user ON a_service_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_service_accounts_service ON a_service_accounts(service_id);
CREATE TRIGGER trg_service_accounts_updated_at BEFORE UPDATE ON a_service_accounts
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();

-- =========================
-- AREAS (groups/graphs)
-- =========================
CREATE TABLE IF NOT EXISTS a_areas (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE, -- owner
  name        text NOT NULL,
  description text,
  enabled     boolean NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_areas_user ON a_areas(user_id, enabled);
CREATE TRIGGER trg_areas_updated_at BEFORE UPDATE ON a_areas
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();
COMMENT ON TABLE a_areas IS 'Group/graph of actions and links created by a user.';

-- (Optional) Sharing/collaboration
CREATE TABLE IF NOT EXISTS a_area_collaborators (
  area_id   uuid NOT NULL REFERENCES a_areas(id) ON DELETE CASCADE,
  user_id   uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE,
  role      text NOT NULL DEFAULT 'editor', -- viewer|editor (to be enforced by app)
  added_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (area_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_area_collab_user ON a_area_collaborators(user_id);
COMMENT ON TABLE a_area_collaborators IS 'Area sharing (collaboration).';

-- =========================
-- Action definitions (technical)
-- =========================
CREATE TABLE IF NOT EXISTS a_action_definitions (
  id                             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  service_id                     uuid NOT NULL REFERENCES a_services(id) ON DELETE CASCADE,
  key                            text NOT NULL,  -- e.g.: "gmail.new_email" or "gmail.send_email"
  name                           text NOT NULL,
  description                    text,
  input_schema                   jsonb NOT NULL DEFAULT '{}'::jsonb,
  output_schema                  jsonb NOT NULL DEFAULT '{}'::jsonb,
  docs_url                       text,
  is_event_capable               boolean NOT NULL DEFAULT false,  -- e.g. "trigger"
  is_executable                  boolean NOT NULL DEFAULT false,  -- e.g. "executable"
  version                        integer NOT NULL DEFAULT 1,
  default_poll_interval_seconds  integer,                         -- if POLL
  throttle_policy                jsonb,                           -- e.g.: {"per_minute":60}
  created_at                     timestamptz NOT NULL DEFAULT now(),
  updated_at                     timestamptz NOT NULL DEFAULT now(),
  UNIQUE (service_id, key, version)
);
CREATE INDEX IF NOT EXISTS idx_action_def_by_service ON a_action_definitions(service_id);
CREATE INDEX IF NOT EXISTS idx_action_def_capabilities ON a_action_definitions(is_event_capable, is_executable);
CREATE TRIGGER trg_action_def_updated_at BEFORE UPDATE ON a_action_definitions
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();
COMMENT ON TABLE a_action_definitions IS 'Technical definitions of service actions (I/O schemas, capabilities).';

-- =========================
-- Action instances (user side)
-- =========================
CREATE TABLE IF NOT EXISTS a_action_instances (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id              uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE,
  area_id              uuid NOT NULL REFERENCES a_areas(id) ON DELETE CASCADE,
  action_def_id        uuid NOT NULL REFERENCES a_action_definitions(id) ON DELETE CASCADE,
  service_account_id   uuid REFERENCES a_service_accounts(id) ON DELETE SET NULL,
  name                 text NOT NULL,
  enabled              boolean NOT NULL DEFAULT true,
  params               jsonb NOT NULL DEFAULT '{}'::jsonb,  -- conforms to input_schema
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_action_instances_user ON a_action_instances(user_id, enabled);
CREATE INDEX IF NOT EXISTS idx_action_instances_area ON a_action_instances(area_id);
CREATE INDEX IF NOT EXISTS idx_action_instances_def ON a_action_instances(action_def_id);
CREATE TRIGGER trg_action_instances_updated_at BEFORE UPDATE ON a_action_instances
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();
COMMENT ON TABLE a_action_instances IS 'Action instances configured by a user (params, account, area).';

-- =========================
-- Activation methods
-- =========================
CREATE TABLE IF NOT EXISTS a_activation_modes (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  action_instance_id   uuid NOT NULL REFERENCES a_action_instances(id) ON DELETE CASCADE,
  type                 activation_mode_type NOT NULL,
  config               jsonb NOT NULL DEFAULT '{}'::jsonb,  -- see examples below
  enabled              boolean NOT NULL DEFAULT true,
  dedup                dedup_strategy NOT NULL DEFAULT 'none',
  max_concurrency      integer,                              -- null = unlimited (managed by worker)
  rate_limit           jsonb,                                -- e.g.: {"per_minute": 60}
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now()
);
-- Example configs:
-- CRON:   {"cron":"*/5 * * * *","timezone":"Europe/Paris"}
-- WEBHOOK:{"path":"/webhooks/gh/abcd","secret":"***","verify":"sha256"}
-- POLL:   {"interval_seconds":60}
-- MANUAL: {}
-- CHAIN:  {"source_action_instance_id":"<uuid>","filter":{...},"condition":{...}}

CREATE INDEX IF NOT EXISTS idx_activation_modes_ai ON a_activation_modes(action_instance_id);
CREATE INDEX IF NOT EXISTS idx_activation_modes_type ON a_activation_modes(type, enabled);
CREATE TRIGGER trg_activation_modes_updated_at BEFORE UPDATE ON a_activation_modes
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();
COMMENT ON TABLE a_activation_modes IS 'Activation methods for an action (CRON/WEBHOOK/POLL/MANUAL/CHAIN).';

-- =========================
-- Chaining between actions (graph edges)
-- =========================
CREATE TABLE IF NOT EXISTS a_action_links (
  source_action_instance_id uuid NOT NULL REFERENCES a_action_instances(id) ON DELETE CASCADE,
  target_action_instance_id uuid NOT NULL REFERENCES a_action_instances(id) ON DELETE CASCADE,
  area_id                   uuid NOT NULL REFERENCES a_areas(id) ON DELETE CASCADE,
  mapping                   jsonb NOT NULL DEFAULT '{}'::jsonb, -- transform source.output -> target.input
  condition                 jsonb,                               -- guards/filters (JMESPath/expr)
  "order"                   integer NOT NULL DEFAULT 0,
  created_at                timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (source_action_instance_id, target_action_instance_id)
);
CREATE INDEX IF NOT EXISTS idx_action_links_source ON a_action_links(source_action_instance_id, "order");
CREATE INDEX IF NOT EXISTS idx_action_links_target ON a_action_links(target_action_instance_id);
CREATE INDEX IF NOT EXISTS idx_action_links_area   ON a_action_links(area_id, "order");
COMMENT ON TABLE a_action_links IS 'Graph edges: chain source -> target with mapping/condition, scoped by area.';

-- Trigger: enforce area consistency (source/target must be in the same area as action_links.area_id)
CREATE OR REPLACE FUNCTION area.enforce_link_area_match()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  src_area uuid;
  dst_area uuid;
BEGIN
  SELECT area_id INTO src_area FROM a_action_instances WHERE id = NEW.source_action_instance_id;
  SELECT area_id INTO dst_area FROM a_action_instances WHERE id = NEW.target_action_instance_id;

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
END$$;

DROP TRIGGER IF EXISTS trg_action_links_area ON a_action_links;
CREATE TRIGGER trg_action_links_area
  BEFORE INSERT OR UPDATE ON a_action_links
  FOR EACH ROW EXECUTE FUNCTION area.enforce_link_area_match();

-- Prevent self-loop
ALTER TABLE a_action_links
  ADD CONSTRAINT chk_action_links_no_self
  CHECK (source_action_instance_id <> target_action_instance_id);

-- =========================
-- Executions (jobs)
-- =========================
CREATE TABLE IF NOT EXISTS a_executions (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  action_instance_id   uuid NOT NULL REFERENCES a_action_instances(id) ON DELETE CASCADE,
  activation_mode_id   uuid REFERENCES a_activation_modes(id) ON DELETE SET NULL,
  area_id              uuid REFERENCES a_areas(id) ON DELETE SET NULL,  -- denormalized (auto via trigger)
  status               execution_status NOT NULL DEFAULT 'QUEUED',
  attempt              integer NOT NULL DEFAULT 0,
  queued_at            timestamptz NOT NULL DEFAULT now(),
  started_at           timestamptz,
  finished_at          timestamptz,
  input_payload        jsonb,
  output_payload       jsonb,
  error                jsonb,
  correlation_id       uuid,                          -- to group a cascade
  dedup_key            text                           -- unique if provided (≠ NULL)
);
-- Index & dedup
CREATE INDEX IF NOT EXISTS idx_exec_ai_status ON a_executions(action_instance_id, status, queued_at);
CREATE INDEX IF NOT EXISTS idx_exec_corr ON a_executions(correlation_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_exec_dedup_notnull ON a_executions(dedup_key) WHERE dedup_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_executions_area_status ON a_executions(area_id, status, queued_at);

-- Trigger: automatically set executions.area_id from the action
CREATE OR REPLACE FUNCTION area.set_execution_area()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  ai_area uuid;
BEGIN
  SELECT area_id INTO ai_area FROM a_action_instances WHERE id = NEW.action_instance_id;
  NEW.area_id := ai_area;
  RETURN NEW;
END$$;

DROP TRIGGER IF EXISTS trg_executions_set_area ON executions;
CREATE TRIGGER trg_executions_set_area
  BEFORE INSERT ON a_executions
  FOR EACH ROW EXECUTE FUNCTION area.set_execution_area();

COMMENT ON TABLE a_executions IS 'Executions log (jobs) with status, retries, I/O, dedup and area.';

-- =========================
-- Emitted events (optional, internal bus)
-- =========================
CREATE TABLE IF NOT EXISTS a_events (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  action_instance_id   uuid NOT NULL REFERENCES a_action_instances(id) ON DELETE CASCADE,
  payload              jsonb NOT NULL,
  emitted_at           timestamptz NOT NULL DEFAULT now(),
  source_event_id      text                                -- external id (for dedup)
);
CREATE INDEX IF NOT EXISTS idx_events_ai_time ON a_events(action_instance_id, emitted_at DESC);
COMMENT ON TABLE a_events IS 'Events emitted by actions (internal bus / history).';

-- =========================
-- Dead letters (final failures)
-- =========================
CREATE TABLE IF NOT EXISTS a_dead_letters (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  execution_id    uuid NOT NULL REFERENCES a_executions(id) ON DELETE CASCADE,
  reason          text,
  payload         jsonb,
  created_at      timestamptz NOT NULL DEFAULT now()
);

-- =========================
-- Audit & Notifications
-- =========================
CREATE TABLE IF NOT EXISTS a_audit_logs (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      uuid REFERENCES a_users(id) ON DELETE SET NULL,
  action       text NOT NULL,                 -- e.g.: "AREA_CREATED", "TOKEN_REFRESHED"
  entity_type  text,
  entity_id    uuid,
  metadata     jsonb,
  ip           inet,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_user_time ON a_audit_logs(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS a_user_notifications (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE,
  channel      text NOT NULL,                 -- "email", "push", "inapp", ...
  payload      jsonb NOT NULL,
  status       text NOT NULL DEFAULT 'queued',-- free-form: queued|sent|failed
  created_at   timestamptz NOT NULL DEFAULT now(),
  sent_at      timestamptz
);
CREATE INDEX IF NOT EXISTS idx_notif_user_status ON a_user_notifications(user_id, status);

-- =========================
-- Compatibility views "about.json"
-- =========================
-- List of event-capable actions (historically "actions")
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

-- List of executable actions (historically "reactions")
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

-- =========================
-- Additional comments
-- =========================
COMMENT ON COLUMN a_services.key IS 'Stable identifier of the service (slug)';
COMMENT ON COLUMN a_activation_modes.config IS 'CRON:{cron,timezone} | WEBHOOK:{path,secret,verify} | POLL:{interval_seconds} | MANUAL:{} | CHAIN:{source_action_instance_id,filter,condition}';