CREATE TABLE work_item_timeline_entries (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  project_id uuid NOT NULL REFERENCES projects(id),
  work_item_id uuid NOT NULL REFERENCES issues(id),
  entry_kind text NOT NULL CHECK (entry_kind IN ('activity', 'comment')),
  entry_kind_rank smallint NOT NULL CHECK (entry_kind_rank IN (0, 1)),
  source_id uuid NOT NULL,
  occurred_at timestamptz NOT NULL,
  deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_work_item_timeline_source UNIQUE (tenant_id, entry_kind, source_id)
);

CREATE INDEX idx_work_item_timeline_item_cursor
  ON work_item_timeline_entries (tenant_id, work_item_id, occurred_at DESC, entry_kind_rank DESC, id DESC)
  WHERE deleted_at IS NULL;

INSERT INTO work_item_timeline_entries (
  id,
  tenant_id,
  project_id,
  work_item_id,
  entry_kind,
  entry_kind_rank,
  source_id,
  occurred_at,
  created_at
)
SELECT
  gen_random_uuid(),
  a.tenant_id,
  a.project_id,
  a.work_item_id,
  'activity',
  1,
  a.id,
  a.occurred_at,
  a.created_at
FROM work_item_activities a
WHERE a.activity_type <> 'work_item.comment.created';

INSERT INTO work_item_timeline_entries (
  id,
  tenant_id,
  project_id,
  work_item_id,
  entry_kind,
  entry_kind_rank,
  source_id,
  occurred_at,
  created_at
)
SELECT
  gen_random_uuid(),
  c.tenant_id,
  i.project_id,
  c.issue_id,
  'comment',
  0,
  c.id,
  c.created_at,
  c.created_at
FROM issue_comments c
JOIN issues i ON i.id = c.issue_id
WHERE c.deleted_at IS NULL;
