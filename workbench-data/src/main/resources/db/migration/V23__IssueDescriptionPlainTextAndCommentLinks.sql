ALTER TABLE issues
  ADD COLUMN IF NOT EXISTS description_plain_text TEXT;

ALTER TABLE issue_comments
  ADD COLUMN IF NOT EXISTS transition_id UUID REFERENCES workflow_transitions(id),
  ADD COLUMN IF NOT EXISTS status_history_id UUID REFERENCES issue_status_history(id);

CREATE INDEX IF NOT EXISTS idx_issue_comments_transition
  ON issue_comments(tenant_id, transition_id)
  WHERE transition_id IS NOT NULL;

INSERT INTO permission_actions (id, code, description)
VALUES (gen_random_uuid(), 'issue.comment.update', 'Update work item comments.')
ON CONFLICT (code) DO NOTHING;
