-- admin_users: who is an administrator at instance or tenant scope
CREATE TABLE admin_users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_id       TEXT NOT NULL UNIQUE,
    user_id      UUID NOT NULL REFERENCES users(id),
    scope        TEXT NOT NULL,
    tenant_id    UUID REFERENCES tenants(id),
    status       TEXT NOT NULL DEFAULT 'active',
    granted_by   UUID REFERENCES users(id),
    valid_from   TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT admin_users_scope_tenant_chk
        CHECK ((scope = 'instance' AND tenant_id IS NULL)
            OR (scope = 'tenant' AND tenant_id IS NOT NULL))
);

CREATE UNIQUE INDEX admin_users_instance_active_uniq
    ON admin_users (user_id)
    WHERE scope = 'instance'
      AND tenant_id IS NULL
      AND status = 'active'
      AND valid_to IS NULL;

CREATE UNIQUE INDEX admin_users_tenant_active_uniq
    ON admin_users (user_id, tenant_id)
    WHERE scope = 'tenant'
      AND tenant_id IS NOT NULL
      AND status = 'active'
      AND valid_to IS NULL;

CREATE INDEX idx_admin_users_user_id ON admin_users(user_id);
CREATE INDEX idx_admin_users_tenant_id ON admin_users(tenant_id) WHERE tenant_id IS NOT NULL;

-- access_grants: flat authorization replacing roles / policies / assignments
CREATE TABLE access_grants (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_id           TEXT NOT NULL UNIQUE,
    scope            TEXT NOT NULL,
    tenant_id        UUID REFERENCES tenants(id),
    project_id       UUID REFERENCES projects(id),
    subject_user_id  UUID NOT NULL REFERENCES users(id),
    action           TEXT NOT NULL REFERENCES permission_actions(code),
    resource_pattern TEXT NOT NULL DEFAULT '*',
    effect           TEXT NOT NULL DEFAULT 'allow',
    valid_from       TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to         TIMESTAMPTZ,
    granted_by       UUID REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT access_grants_scope_chk
        CHECK ((scope = 'instance' AND tenant_id IS NULL AND project_id IS NULL)
            OR (scope = 'tenant' AND tenant_id IS NOT NULL AND project_id IS NULL)
            OR (scope = 'project' AND tenant_id IS NOT NULL AND project_id IS NOT NULL))
);

CREATE INDEX idx_access_grants_subject ON access_grants(subject_user_id);
CREATE INDEX idx_access_grants_tenant ON access_grants(tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_access_grants_scope ON access_grants(scope);

-- instance administrator login method (separate from tenant password)
INSERT INTO login_method_definitions (
    id,
    api_id,
    code,
    kind,
    name,
    is_builtin,
    is_enabled_globally,
    config_schema,
    created_at,
    updated_at
)
VALUES (
    gen_random_uuid(),
    'lmg_01JABCDEFGHJKMNPQRSTVWXYZ1',
    'instance_password',
    'password',
    'Workbench Admin',
    true,
    true,
    '{"credential":"password"}',
    now(),
    now()
)
ON CONFLICT (code) DO NOTHING;

-- migrate system users to instance admin_users
INSERT INTO admin_users (id, api_id, user_id, scope, tenant_id, status, granted_by, valid_from, created_at, updated_at)
SELECT
    gen_random_uuid(),
    'adu_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 26),
    u.id,
    'instance',
    NULL,
    'active',
    NULL,
    now(),
    now(),
    now()
FROM users u
WHERE u.is_system = true
  AND u.deleted_at IS NULL;

-- default instance admin grants for migrated admins
INSERT INTO access_grants (id, api_id, scope, tenant_id, project_id, subject_user_id, action, resource_pattern, effect, valid_from, created_at)
SELECT
    gen_random_uuid(),
    'agr_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 26),
    'instance',
    NULL,
    NULL,
    au.user_id,
    action_code,
    'tenant:*',
    'allow',
    now(),
    now()
FROM admin_users au
CROSS JOIN (VALUES ('tenant.create'), ('tenant.read'), ('tenant.update')) AS actions(action_code)
WHERE au.scope = 'instance'
  AND au.status = 'active'
  AND au.valid_to IS NULL;

-- flatten role_assignments + permission_policies into access_grants
INSERT INTO access_grants (
    id, api_id, scope, tenant_id, project_id, subject_user_id,
    action, resource_pattern, effect, valid_from, valid_to, granted_by, created_at
)
SELECT
    gen_random_uuid(),
    'agr_' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 26),
    CASE WHEN ra.project_id IS NOT NULL THEN 'project' ELSE 'tenant' END,
    ra.tenant_id,
    ra.project_id,
    ra.user_id,
    pa.code,
    pp.resource_pattern,
    lower(pp.effect),
    GREATEST(ra.valid_from, pp.valid_from),
    LEAST(ra.valid_to, pp.valid_to),
    ra.granted_by,
    now()
FROM role_assignments ra
JOIN permission_policies pp
    ON pp.role_id = ra.role_id AND pp.tenant_id = ra.tenant_id
JOIN permission_actions pa ON pa.id = pp.action_id
WHERE (ra.valid_to IS NULL OR ra.valid_to > now())
  AND (pp.valid_to IS NULL OR pp.valid_to > now());

-- drop legacy RBAC tables
DROP TABLE role_assignments;
DROP TABLE permission_policies;
DROP TABLE roles;

-- remove is_system from users
ALTER TABLE users DROP COLUMN is_system;
