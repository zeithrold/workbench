CREATE TABLE issue_type_config_access_rules (
  id UUID PRIMARY KEY,
  api_id TEXT NOT NULL UNIQUE,
  tenant_id UUID NOT NULL REFERENCES tenants (id),
  issue_type_config_id UUID NOT NULL REFERENCES issue_type_configs (id),
  subject_type TEXT NOT NULL,
  subject_user_id UUID REFERENCES users (id),
  subject_group_id UUID REFERENCES groups (id),
  subject_role_code TEXT,
  action_type TEXT NOT NULL,
  transition_id UUID REFERENCES workflow_transitions (id),
  field_key TEXT,
  effect TEXT NOT NULL,
  condition_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  rank INTEGER NOT NULL DEFAULT 100,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_issue_type_config_access_rules_config
  ON issue_type_config_access_rules (issue_type_config_id)
  WHERE is_active = TRUE;

CREATE INDEX idx_issue_type_config_access_rules_tenant
  ON issue_type_config_access_rules (tenant_id);
