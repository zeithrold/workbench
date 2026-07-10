ALTER TABLE sprints
  DROP CONSTRAINT IF EXISTS sprints_status_check;

CREATE TABLE sprint_close_operations (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID NOT NULL REFERENCES projects(id),
    sprint_id UUID NOT NULL REFERENCES sprints(id),
    target_sprint_id UUID REFERENCES sprints(id),
    disposition TEXT NOT NULL,
    requested_by UUID NOT NULL REFERENCES users(id),
    status TEXT NOT NULL,
    total_items INTEGER NOT NULL DEFAULT 0,
    processed_items INTEGER NOT NULL DEFAULT 0,
    failed_items INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    idempotency_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT sprint_close_operations_disposition_check
      CHECK (disposition IN ('BACKLOG', 'NEXT_SPRINT', 'KEEP')),
    CONSTRAINT sprint_close_operations_status_check
      CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE UNIQUE INDEX uq_sprint_close_operation_idempotency
  ON sprint_close_operations (tenant_id, project_id, sprint_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_sprint_close_operations_lookup
  ON sprint_close_operations (tenant_id, project_id, sprint_id, created_at DESC);

INSERT INTO permission_actions (id, code, description)
VALUES
  (gen_random_uuid(), 'sprint.workitem.disposition',
   'Change work item sprint membership while closing a sprint.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permission_policy_rules (
    id, api_id, policy_id, action, resource_pattern, effect, condition_json, created_at
)
SELECT
    gen_random_uuid(),
    'prl_' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 26)),
    pp.id,
    'sprint.workitem.disposition',
    'sprint:*',
    'allow',
    NULL,
    now()
FROM permission_policies pp
WHERE pp.code IN ('tenant-admin', 'project-admin', 'project-member')
  AND NOT EXISTS (
    SELECT 1
    FROM permission_policy_rules existing
    WHERE existing.policy_id = pp.id
      AND existing.action = 'sprint.workitem.disposition'
      AND existing.resource_pattern = 'sprint:*'
  );
