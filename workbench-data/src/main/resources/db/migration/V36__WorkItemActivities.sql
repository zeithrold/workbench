CREATE TABLE work_item_activities (
  id uuid PRIMARY KEY,
  api_id text NOT NULL UNIQUE,
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  project_id uuid NOT NULL REFERENCES projects(id),
  work_item_id uuid NOT NULL REFERENCES issues(id),
  actor_user_id uuid REFERENCES users(id),
  activity_type text NOT NULL,
  occurred_at timestamptz NOT NULL,
  summary text,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  source_type text NOT NULL DEFAULT 'user',
  source_id text,
  correlation_id text,
  request_id text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_item_activities_item_time
  ON work_item_activities (tenant_id, work_item_id, occurred_at DESC, id DESC);

CREATE INDEX idx_work_item_activities_project_time
  ON work_item_activities (tenant_id, project_id, occurred_at DESC, id DESC);

CREATE INDEX idx_work_item_activities_type_time
  ON work_item_activities (tenant_id, activity_type, occurred_at DESC);

ALTER TABLE issue_comments
  ADD COLUMN IF NOT EXISTS activity_id uuid REFERENCES work_item_activities(id);
