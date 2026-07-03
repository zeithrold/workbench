INSERT INTO permission_actions (id, code, description)
VALUES
    (gen_random_uuid(), 'project.archive', 'Archive or unarchive projects.'),
    (gen_random_uuid(), 'project.join', 'Join an open project as a member.')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE projects
    ADD COLUMN status TEXT NOT NULL DEFAULT 'active',
    ADD COLUMN non_member_visibility TEXT NOT NULL DEFAULT 'invisible',
    ADD COLUMN non_member_join_policy TEXT NOT NULL DEFAULT 'admin_only';

ALTER TABLE projects
    ADD CONSTRAINT projects_status_chk
        CHECK (status IN ('active', 'archived', 'destroying'));

ALTER TABLE projects
    ADD CONSTRAINT projects_non_member_visibility_chk
        CHECK (non_member_visibility IN ('invisible', 'read_only', 'read_write'));

ALTER TABLE projects
    ADD CONSTRAINT projects_non_member_join_policy_chk
        CHECK (non_member_join_policy IN ('open', 'admin_only'));

CREATE INDEX idx_projects_tenant_status ON projects(tenant_id, status) WHERE deleted_at IS NULL;

INSERT INTO permission_policy_rules (
    id, api_id, policy_id, action, resource_pattern, effect, condition_json, created_at
)
SELECT
    gen_random_uuid(),
    'prl_' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 26)),
    pp.id,
    rule.action,
    rule.resource_pattern,
    'allow',
    NULL,
    now()
FROM permission_policies pp
JOIN (
    VALUES
        ('tenant-admin', 'project.archive', 'project:*'),
        ('project-admin', 'project.archive', 'project:*')
) AS rule(policy_code, action, resource_pattern)
    ON rule.policy_code = pp.code
WHERE NOT EXISTS (
    SELECT 1
    FROM permission_policy_rules existing
    WHERE existing.policy_id = pp.id
      AND existing.action = rule.action
);
