ALTER TABLE domain_outbox
  ADD COLUMN event_id text,
  ADD COLUMN event_type text,
  ADD COLUMN event_version int NOT NULL DEFAULT 1,
  ADD COLUMN tenant_id text,
  ADD COLUMN status text NOT NULL DEFAULT 'PENDING',
  ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN locked_until timestamptz,
  ADD COLUMN last_error text;

INSERT INTO permission_actions (id, code, description)
VALUES
  (gen_random_uuid(), 'notification.read', 'Read notifications.'),
  (gen_random_uuid(), 'notification.manage', 'Manage notification preferences.')
ON CONFLICT (code) DO NOTHING;

UPDATE domain_outbox
SET event_id = id::text,
    event_type = 'legacy.domain_event'
WHERE event_id IS NULL;

ALTER TABLE domain_outbox
  ALTER COLUMN event_id SET NOT NULL,
  ALTER COLUMN event_type SET NOT NULL;

CREATE UNIQUE INDEX uq_domain_outbox_event_id ON domain_outbox (event_id);
DROP INDEX IF EXISTS idx_domain_outbox_unpublished;
CREATE INDEX idx_domain_outbox_pending
  ON domain_outbox (next_attempt_at, created_at)
  WHERE status IN ('PENDING', 'RETRY');

CREATE TABLE notifications (
  id uuid PRIMARY KEY,
  api_id text NOT NULL UNIQUE,
  recipient_user_id uuid NOT NULL REFERENCES users(id),
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  project_id uuid REFERENCES projects(id),
  work_item_id uuid REFERENCES issues(id),
  source_event_id text NOT NULL,
  notification_type text NOT NULL,
  title text NOT NULL,
  body text NOT NULL,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  read_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_notification_dedupe UNIQUE
    (source_event_id, recipient_user_id, notification_type)
);

CREATE INDEX idx_notifications_recipient_created
  ON notifications (recipient_user_id, tenant_id, created_at DESC);

CREATE TABLE notification_deliveries (
  id uuid PRIMARY KEY,
  notification_id uuid NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  channel text NOT NULL,
  status text NOT NULL,
  attempts int NOT NULL DEFAULT 0,
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  sent_at timestamptz,
  last_error text,
  CONSTRAINT uq_notification_delivery_channel UNIQUE (notification_id, channel)
);

CREATE TABLE notification_preferences (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  notification_type text NOT NULL,
  in_app_enabled boolean NOT NULL DEFAULT true,
  email_enabled boolean NOT NULL DEFAULT true,
  PRIMARY KEY (user_id, notification_type)
);

CREATE TABLE processed_domain_events (
  consumer_name text NOT NULL,
  event_id text NOT NULL,
  processed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer_name, event_id)
);
