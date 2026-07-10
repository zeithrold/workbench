INSERT INTO permission_actions (id, code, description)
VALUES
  (gen_random_uuid(), 'outbox.read', 'Read domain outbox messages.'),
  (gen_random_uuid(), 'outbox.manage', 'Manage domain outbox messages including replay.')
ON CONFLICT (code) DO NOTHING;

-- Instance administrators are identities only. Their capabilities are explicit
-- instance-scope grants and must not inherit tenant/project administration.
UPDATE access_grants
SET valid_to = now()
WHERE scope = 'instance'
  AND valid_to IS NULL
  AND (action LIKE 'tenant.%' OR action LIKE 'project.%');

INSERT INTO access_grants (
  id, api_id, scope, tenant_id, project_id, subject_user_id, action,
  resource_pattern, effect, valid_from, granted_by, created_at
)
SELECT
  gen_random_uuid(),
  'agr_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 26),
  'instance',
  NULL,
  NULL,
  au.user_id,
  action.code,
  'outbox',
  'allow',
  now(),
  NULL,
  now()
FROM admin_users au
CROSS JOIN (
  VALUES ('outbox.read'), ('outbox.manage')
) AS action(code)
WHERE au.scope = 'instance'
  AND au.status = 'active'
  AND au.valid_to IS NULL
  AND NOT EXISTS (
    SELECT 1
    FROM access_grants ag
    WHERE ag.scope = 'instance'
      AND ag.tenant_id IS NULL
      AND ag.project_id IS NULL
      AND ag.subject_user_id = au.user_id
      AND ag.action = action.code
      AND ag.resource_pattern = 'outbox'
      AND ag.effect = 'allow'
      AND ag.valid_to IS NULL
  );
