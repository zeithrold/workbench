-- Work item stream: replace async activity pipeline with synchronous event log + timeline projection.

CREATE TABLE work_item_events (
  id uuid PRIMARY KEY,
  api_id text NOT NULL UNIQUE,
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  project_id uuid NOT NULL REFERENCES projects(id),
  work_item_id uuid NOT NULL REFERENCES issues(id),
  sequence bigint NOT NULL,
  event_type text NOT NULL,
  occurred_at timestamptz NOT NULL,
  actor_user_id uuid REFERENCES users(id),
  summary text,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  source_type text NOT NULL DEFAULT 'user',
  source_id text,
  correlation_id text,
  request_id text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_work_item_events_sequence UNIQUE (work_item_id, sequence)
);

CREATE INDEX idx_work_item_events_timeline
  ON work_item_events (tenant_id, work_item_id, sequence DESC);

CREATE TABLE domain_outbox (
  id uuid PRIMARY KEY,
  topic text NOT NULL,
  partition_key text NOT NULL,
  payload jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  published_at timestamptz,
  attempts int NOT NULL DEFAULT 0
);

CREATE INDEX idx_domain_outbox_unpublished
  ON domain_outbox (created_at)
  WHERE published_at IS NULL;

-- Unified stream rows: legacy activities (except comment.created) + comment.added from comments.
WITH legacy_activity_rows AS (
  SELECT
    a.id,
    a.api_id,
    a.tenant_id,
    a.project_id,
    a.work_item_id,
    a.actor_user_id,
    CASE a.activity_type
      WHEN 'work_item.created' THEN 'work_item.created'
      WHEN 'work_item.updated' THEN 'work_item.updated'
      WHEN 'work_item.status_changed' THEN 'work_item.status_changed'
      ELSE NULL
    END AS event_type,
    a.occurred_at,
    a.summary,
    a.payload,
    a.source_type,
    a.source_id,
    a.correlation_id,
    a.request_id,
    a.created_at
  FROM work_item_activities a
  WHERE a.activity_type <> 'work_item.comment.created'
),
comment_rows AS (
  SELECT
    gen_random_uuid() AS id,
    'evt_' || replace(c.api_id, 'icm_', '') AS api_id,
    c.tenant_id,
    i.project_id,
    c.issue_id AS work_item_id,
    c.author_id AS actor_user_id,
    'comment.added' AS event_type,
    c.created_at AS occurred_at,
    left(coalesce(c.body_plain_text, ''), 200) AS summary,
    jsonb_build_object(
      'comment',
      jsonb_build_object(
        'id', c.api_id,
        'plainTextPreview', left(coalesce(c.body_plain_text, ''), 200)
      )
    ) AS payload,
    'user' AS source_type,
    c.api_id AS source_id,
    NULL::text AS correlation_id,
    NULL::text AS request_id,
    c.created_at
  FROM issue_comments c
  JOIN issues i ON i.id = c.issue_id
),
combined AS (
  SELECT * FROM legacy_activity_rows WHERE event_type IS NOT NULL
  UNION ALL
  SELECT * FROM comment_rows
),
ranked AS (
  SELECT
    c.*,
    row_number() OVER (
      PARTITION BY c.work_item_id
      ORDER BY c.occurred_at ASC, c.api_id ASC
    ) AS sequence
  FROM combined c
)
INSERT INTO work_item_events (
  id,
  api_id,
  tenant_id,
  project_id,
  work_item_id,
  sequence,
  event_type,
  occurred_at,
  actor_user_id,
  summary,
  payload,
  source_type,
  source_id,
  correlation_id,
  request_id,
  created_at
)
SELECT
  id,
  api_id,
  tenant_id,
  project_id,
  work_item_id,
  sequence,
  event_type,
  occurred_at,
  actor_user_id,
  summary,
  payload,
  source_type,
  source_id,
  correlation_id,
  request_id,
  created_at
FROM ranked;

DROP TABLE work_item_timeline_entries;

CREATE TABLE work_item_timeline_entries (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  project_id uuid NOT NULL REFERENCES projects(id),
  work_item_id uuid NOT NULL REFERENCES issues(id),
  event_id uuid NOT NULL REFERENCES work_item_events(id),
  sequence bigint NOT NULL,
  occurred_at timestamptz NOT NULL,
  deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_work_item_timeline_event UNIQUE (work_item_id, event_id),
  CONSTRAINT uq_work_item_timeline_sequence UNIQUE (work_item_id, sequence)
);

CREATE INDEX idx_work_item_timeline_cursor
  ON work_item_timeline_entries (tenant_id, work_item_id, sequence DESC)
  WHERE deleted_at IS NULL;

INSERT INTO work_item_timeline_entries (
  id,
  tenant_id,
  project_id,
  work_item_id,
  event_id,
  sequence,
  occurred_at,
  deleted_at,
  created_at
)
SELECT
  gen_random_uuid(),
  e.tenant_id,
  e.project_id,
  e.work_item_id,
  e.id,
  e.sequence,
  e.occurred_at,
  CASE
    WHEN e.event_type = 'comment.added' AND c.deleted_at IS NOT NULL THEN c.deleted_at
    ELSE NULL
  END,
  e.created_at
FROM work_item_events e
LEFT JOIN issue_comments c
  ON e.event_type = 'comment.added'
 AND e.source_id = c.api_id
 AND c.tenant_id = e.tenant_id;

DROP TABLE work_item_activities;

ALTER TABLE issue_comments DROP COLUMN IF EXISTS activity_id;
