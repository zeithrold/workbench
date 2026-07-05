INSERT INTO permission_actions (id, code, description)
VALUES
    (gen_random_uuid(), 'issue.attachment.create', 'Create work item attachments.'),
    (gen_random_uuid(), 'issue.attachment.delete', 'Delete work item attachments.')
ON CONFLICT (code) DO NOTHING;

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
        ('tenant-admin', 'issue.attachment.create', 'issue:*'),
        ('project-admin', 'issue.attachment.create', 'issue:*'),
        ('project-member', 'issue.attachment.create', 'issue:*'),
        ('tenant-admin', 'issue.attachment.delete', 'issue:*'),
        ('project-admin', 'issue.attachment.delete', 'issue:*'),
        ('project-member', 'issue.attachment.delete', 'issue:*')
) AS rule(policy_code, action, resource_pattern)
    ON rule.policy_code = pp.code
WHERE NOT EXISTS (
    SELECT 1
    FROM permission_policy_rules existing
    WHERE existing.policy_id = pp.id
      AND existing.action = rule.action
      AND existing.resource_pattern = rule.resource_pattern
);
