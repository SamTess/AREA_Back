-- ============================================
-- AREA - Schéma unifié (Actions + Areas)
-- PostgreSQL 13+
-- =======================================-- (Optionnel) Partage/collab=
-- Extensions utiles
-- =========================
CREATE EXTENSION IF NOT EXISTS pgcrypto;      -- gen_random_uuid(), digest()
CREATE EXTENSION IF NOT EXISTS citext;        -- emails insensibles à la casse (optionnel)

-- Schéma dédié (optionnel)
CREATE SCHEMA IF NOT EXISTS area;
SET search_path TO area, public;

-- =========================
-- Types énumérés
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
-- Fonctions / triggers communs
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
-- Utilisateurs & Identité
-- =========================
CREATE TABLE IF NOT EXISTS a_users (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email            citext UNIQUE NOT NULL,
  password_hash    text,                           -- nullable si compte uniquement OAuth
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
  provider         text NOT NULL,                  -- ex: google, github
  provider_user_id text NOT NULL,
  access_token_enc text,
  refresh_token_enc text,
  expires_at       timestamptz,
  scopes           jsonb,                          -- lisible
  token_meta       jsonb,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, provider)
);
CREATE TRIGGER trg_uoi_updated_at BEFORE UPDATE ON a_user_oauth_identities
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();

-- Table pour les identités locales (connexion email/mot de passe)
CREATE TABLE IF NOT EXISTS a_user_local_identities (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE,
  email            citext UNIQUE NOT NULL,         -- email de connexion (peut différer de users.email)
  password_hash    text NOT NULL,                  -- hash bcrypt du mot de passe
  salt             text,                           -- sel additionnel si nécessaire
  is_email_verified boolean NOT NULL DEFAULT false,
  email_verification_token text,                   -- token pour vérification email
  email_verification_expires_at timestamptz,      -- expiration du token
  password_reset_token text,                       -- token pour reset mot de passe
  password_reset_expires_at timestamptz,          -- expiration du token reset
  failed_login_attempts integer NOT NULL DEFAULT 0,
  locked_until     timestamptz,                    -- verrouillage temporaire après échecs
  last_password_change_at timestamptz,            -- dernière modification du mot de passe
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id)                                 -- un seul compte local par utilisateur
);
CREATE INDEX IF NOT EXISTS idx_user_local_identities_email ON a_user_local_identities(email);
CREATE INDEX IF NOT EXISTS idx_user_local_identities_verification ON a_user_local_identities(email_verification_token) WHERE email_verification_token IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_user_local_identities_reset ON a_user_local_identities(password_reset_token) WHERE password_reset_token IS NOT NULL;
CREATE TRIGGER trg_uli_updated_at BEFORE UPDATE ON a_user_local_identities
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();

-- =========================
-- Catalogue des services
-- =========================
CREATE TABLE IF NOT EXISTS a_services (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  key              text UNIQUE NOT NULL,           -- ex: "gmail", "github"
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

-- Comptes reliés à un service (tokens, secrets)
CREATE TABLE IF NOT EXISTS a_service_accounts (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id            uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE,
  service_id         uuid NOT NULL REFERENCES a_services(id) ON DELETE CASCADE,
  remote_account_id  text,                          -- id côté service (optionnel)
  access_token_enc   text,
  refresh_token_enc  text,
  expires_at         timestamptz,
  scopes             jsonb,
  webhook_secret_enc text,                          -- pour vérif de signature
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
-- AREAS (groupes/graphes)
-- =========================
CREATE TABLE IF NOT EXISTS a_areas (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE, -- propriétaire
  name        text NOT NULL,
  description text,
  enabled     boolean NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_areas_user ON a_areas(user_id, enabled);
CREATE TRIGGER trg_areas_updated_at BEFORE UPDATE ON a_areas
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();
COMMENT ON TABLE a_areas IS 'Groupe/graph d’actions et de liens créé par un utilisateur.';

-- (Optionnel) Partage/collab
CREATE TABLE IF NOT EXISTS a_area_collaborators (
  area_id   uuid NOT NULL REFERENCES a_areas(id) ON DELETE CASCADE,
  user_id   uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE,
  role      text NOT NULL DEFAULT 'editor', -- viewer|editor (à appliquer côté app)
  added_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (area_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_area_collab_user ON a_area_collaborators(user_id);
COMMENT ON TABLE a_area_collaborators IS 'Partage des areas (collab).';

-- =========================
-- Définitions d'actions (techniques)
-- =========================
CREATE TABLE IF NOT EXISTS a_action_definitions (
  id                             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  service_id                     uuid NOT NULL REFERENCES a_services(id) ON DELETE CASCADE,
  key                            text NOT NULL,  -- ex: "gmail.new_email" ou "gmail.send_email"
  name                           text NOT NULL,
  description                    text,
  input_schema                   jsonb NOT NULL DEFAULT '{}'::jsonb,
  output_schema                  jsonb NOT NULL DEFAULT '{}'::jsonb,
  docs_url                       text,
  is_event_capable               boolean NOT NULL DEFAULT false,  -- ex "trigger"
  is_executable                  boolean NOT NULL DEFAULT false,  -- ex "exécutable"
  version                        integer NOT NULL DEFAULT 1,
  default_poll_interval_seconds  integer,                         -- si POLL
  throttle_policy                jsonb,                           -- ex: {"per_minute":60}
  created_at                     timestamptz NOT NULL DEFAULT now(),
  updated_at                     timestamptz NOT NULL DEFAULT now(),
  UNIQUE (service_id, key, version)
);
CREATE INDEX IF NOT EXISTS idx_action_def_by_service ON a_action_definitions(service_id);
CREATE INDEX IF NOT EXISTS idx_action_def_capabilities ON a_action_definitions(is_event_capable, is_executable);
CREATE TRIGGER trg_action_def_updated_at BEFORE UPDATE ON a_action_definitions
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();
COMMENT ON TABLE a_action_definitions IS 'Définitions techniques d’actions d’un service (schémas I/O, capacités).';

-- =========================
-- Instances d'action (côté utilisateur)
-- =========================
CREATE TABLE IF NOT EXISTS a_action_instances (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id              uuid NOT NULL REFERENCES a_users(id) ON DELETE CASCADE,
  area_id              uuid NOT NULL REFERENCES a_areas(id) ON DELETE CASCADE,
  action_def_id        uuid NOT NULL REFERENCES a_action_definitions(id) ON DELETE CASCADE,
  service_account_id   uuid REFERENCES a_service_accounts(id) ON DELETE SET NULL,
  name                 text NOT NULL,
  enabled              boolean NOT NULL DEFAULT true,
  params               jsonb NOT NULL DEFAULT '{}'::jsonb,  -- conforme à input_schema
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_action_instances_user ON a_action_instances(user_id, enabled);
CREATE INDEX IF NOT EXISTS idx_action_instances_area ON a_action_instances(area_id);
CREATE INDEX IF NOT EXISTS idx_action_instances_def ON a_action_instances(action_def_id);
CREATE TRIGGER trg_action_instances_updated_at BEFORE UPDATE ON a_action_instances
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();
COMMENT ON TABLE a_action_instances IS 'Instances d''actions configurées par un utilisateur (params, compte, area).';

-- =========================
-- Méthodes d'activation
-- =========================
CREATE TABLE IF NOT EXISTS a_activation_modes (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  action_instance_id   uuid NOT NULL REFERENCES a_action_instances(id) ON DELETE CASCADE,
  type                 activation_mode_type NOT NULL,
  config               jsonb NOT NULL DEFAULT '{}'::jsonb,  -- voir exemples ci-dessous
  enabled              boolean NOT NULL DEFAULT true,
  dedup                dedup_strategy NOT NULL DEFAULT 'none',
  max_concurrency      integer,                              -- null = illimité (géré côté worker)
  rate_limit           jsonb,                                -- ex: {"per_minute": 60}
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now()
);
-- Exemples de config:
-- CRON:   {"cron":"*/5 * * * *","timezone":"Europe/Paris"}
-- WEBHOOK:{"path":"/webhooks/gh/abcd","secret":"***","verify":"sha256"}
-- POLL:   {"interval_seconds":60}
-- MANUAL: {}
-- CHAIN:  {"source_action_instance_id":"<uuid>","filter":{...},"condition":{...}}

CREATE INDEX IF NOT EXISTS idx_activation_modes_ai ON a_activation_modes(action_instance_id);
CREATE INDEX IF NOT EXISTS idx_activation_modes_type ON a_activation_modes(type, enabled);
CREATE TRIGGER trg_activation_modes_updated_at BEFORE UPDATE ON a_activation_modes
  FOR EACH ROW EXECUTE FUNCTION area.set_updated_at();
COMMENT ON TABLE a_activation_modes IS 'Méthodes d''activation d''une action (CRON/WEBHOOK/POLL/MANUAL/CHAIN).';

-- =========================
-- Chaînage entre actions (arêtes du graphe)
-- =========================
CREATE TABLE IF NOT EXISTS a_action_links (
  source_action_instance_id uuid NOT NULL REFERENCES a_action_instances(id) ON DELETE CASCADE,
  target_action_instance_id uuid NOT NULL REFERENCES a_action_instances(id) ON DELETE CASCADE,
  area_id                   uuid NOT NULL REFERENCES a_areas(id) ON DELETE CASCADE,
  mapping                   jsonb NOT NULL DEFAULT '{}'::jsonb, -- transform source.output -> target.input
  condition                 jsonb,                               -- garde/filtres (JMESPath/expr)
  "order"                   integer NOT NULL DEFAULT 0,
  created_at                timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (source_action_instance_id, target_action_instance_id)
);
CREATE INDEX IF NOT EXISTS idx_action_links_source ON a_action_links(source_action_instance_id, "order");
CREATE INDEX IF NOT EXISTS idx_action_links_target ON a_action_links(target_action_instance_id);
CREATE INDEX IF NOT EXISTS idx_action_links_area   ON a_action_links(area_id, "order");
COMMENT ON TABLE a_action_links IS 'Arêtes du graphe: chaînage source -> cible avec mapping/condition, scoping par area.';

-- Trigger : forcer la cohérence d’area (source/target dans le même area que action_links.area_id)
CREATE OR REPLACE FUNCTION area.enforce_link_area_match()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  src_area uuid;
  dst_area uuid;
BEGIN
  SELECT area_id INTO src_area FROM a_action_instances WHERE id = NEW.source_action_instance_id;
  SELECT area_id INTO dst_area FROM a_action_instances WHERE id = NEW.target_action_instance_id;

  IF src_area IS NULL OR dst_area IS NULL THEN
    RAISE EXCEPTION 'action_links: source/target area_id introuvable';
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

-- Interdire self-loop
ALTER TABLE a_action_links
  ADD CONSTRAINT chk_action_links_no_self
  CHECK (source_action_instance_id <> target_action_instance_id);

-- =========================
-- Exécutions (jobs)
-- =========================
CREATE TABLE IF NOT EXISTS a_executions (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  action_instance_id   uuid NOT NULL REFERENCES a_action_instances(id) ON DELETE CASCADE,
  activation_mode_id   uuid REFERENCES a_activation_modes(id) ON DELETE SET NULL,
  area_id              uuid REFERENCES a_areas(id) ON DELETE SET NULL,  -- dénormalisé (auto via trigger)
  status               execution_status NOT NULL DEFAULT 'QUEUED',
  attempt              integer NOT NULL DEFAULT 0,
  queued_at            timestamptz NOT NULL DEFAULT now(),
  started_at           timestamptz,
  finished_at          timestamptz,
  input_payload        jsonb,
  output_payload       jsonb,
  error                jsonb,
  correlation_id       uuid,                          -- pour regrouper une cascade
  dedup_key            text                           -- unique si fourni (≠ NULL)
);
-- Index & dédup
CREATE INDEX IF NOT EXISTS idx_exec_ai_status ON a_executions(action_instance_id, status, queued_at);
CREATE INDEX IF NOT EXISTS idx_exec_corr ON a_executions(correlation_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_exec_dedup_notnull ON a_executions(dedup_key) WHERE dedup_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_executions_area_status ON a_executions(area_id, status, queued_at);

-- Trigger : setter automatiquement executions.area_id depuis l’action
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

COMMENT ON TABLE a_executions IS 'Journal d’exécutions (jobs) avec statut, retries, I/O, dédup et area.';

-- =========================
-- Événements émis (optionnel, bus interne)
-- =========================
CREATE TABLE IF NOT EXISTS a_events (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  action_instance_id   uuid NOT NULL REFERENCES a_action_instances(id) ON DELETE CASCADE,
  payload              jsonb NOT NULL,
  emitted_at           timestamptz NOT NULL DEFAULT now(),
  source_event_id      text                                -- id externe (pour dédup)
);
CREATE INDEX IF NOT EXISTS idx_events_ai_time ON a_events(action_instance_id, emitted_at DESC);
COMMENT ON TABLE a_events IS 'Événements émis par des actions (bus interne / historique).';

-- =========================
-- Dead letters (échecs finaux)
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
  action       text NOT NULL,                 -- ex: "AREA_CREATED", "TOKEN_REFRESHED"
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
  status       text NOT NULL DEFAULT 'queued',-- libre: queued|sent|failed
  created_at   timestamptz NOT NULL DEFAULT now(),
  sent_at      timestamptz
);
CREATE INDEX IF NOT EXISTS idx_notif_user_status ON a_user_notifications(user_id, status);

-- =========================
-- Vues de compatibilité "about.json"
-- =========================
-- Liste des actions "capables d'émettre" (historiquement "actions")
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

-- Liste des actions "exécutables" (historiquement "reactions")
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
-- Commentaires supplémentaires
-- =========================
COMMENT ON COLUMN a_services.key IS 'Identifiant stable du service (slug)';
COMMENT ON COLUMN a_activation_modes.config IS 'CRON:{cron,timezone} | WEBHOOK:{path,secret,verify} | POLL:{interval_seconds} | MANUAL:{} | CHAIN:{source_action_instance_id,filter,condition}';