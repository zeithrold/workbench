INSERT INTO permission_actions (code, description)
VALUES
    ('instance.read', 'Read instance identity and status.'),
    ('instance.admin.manage', 'Manage instance administrators.'),
    ('operations.read', 'Read instance operational health and metrics.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO access_grants (
    api_id,
    scope,
    subject_user_id,
    action,
    resource_pattern,
    effect,
    valid_from,
    granted_by
)
SELECT
    'agr_' || upper(substr(md5(admin.id::text || capability.action), 1, 26)),
    'instance',
    admin.user_id,
    capability.action,
    capability.resource_pattern,
    'allow',
    admin.valid_from,
    admin.granted_by
FROM admin_users admin
CROSS JOIN (
    VALUES
        ('instance.read', 'instance:*'),
        ('instance.admin.manage', 'instance-admin:*'),
        ('tenant.create', 'tenant:*'),
        ('tenant.read', 'tenant:*'),
        ('tenant.update', 'tenant:*'),
        ('tenant.delete', 'tenant:*'),
        ('operations.read', 'operations:*'),
        ('outbox.read', 'outbox:*'),
        ('outbox.manage', 'outbox:*')
) AS capability(action, resource_pattern)
WHERE admin.scope = 'instance'
  AND admin.status = 'active'
  AND admin.valid_to IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM access_grants grant_row
      WHERE grant_row.scope = 'instance'
        AND grant_row.subject_user_id = admin.user_id
        AND grant_row.action = capability.action
        AND grant_row.resource_pattern = capability.resource_pattern
        AND grant_row.valid_to IS NULL
  );
