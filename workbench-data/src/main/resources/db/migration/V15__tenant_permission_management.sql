INSERT INTO permission_actions (id, code, description)
VALUES
    (gen_random_uuid(), 'permission.group.manage', 'Manage tenant permission groups.'),
    (gen_random_uuid(), 'tenant.member.manage', 'Manage tenant members.'),
    (gen_random_uuid(), 'project.update', 'Update projects.'),
    (gen_random_uuid(), 'project.delete', 'Delete projects.'),
    (gen_random_uuid(), 'tenant.delete', 'Delete tenants.')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE groups (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_id      TEXT NOT NULL UNIQUE,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    code        TEXT NOT NULL,
    name        TEXT NOT NULL,
    description TEXT,
    builtin     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT groups_tenant_code_uniq UNIQUE (tenant_id, code)
);

CREATE INDEX idx_groups_tenant_id ON groups(tenant_id);

CREATE TABLE group_members (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_id     TEXT NOT NULL UNIQUE,
    group_id   UUID NOT NULL REFERENCES groups(id),
    user_id    UUID NOT NULL REFERENCES users(id),
    status     TEXT NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX group_members_active_uniq
    ON group_members (group_id, user_id)
    WHERE status = 'active';

CREATE INDEX idx_group_members_user_id ON group_members(user_id);

CREATE TABLE permission_policies (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_id      TEXT NOT NULL UNIQUE,
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    code        TEXT NOT NULL,
    name        TEXT NOT NULL,
    description TEXT,
    builtin     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT permission_policies_tenant_code_uniq UNIQUE (tenant_id, code)
);

CREATE INDEX idx_permission_policies_tenant_id ON permission_policies(tenant_id);

CREATE TABLE permission_policy_rules (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_id           TEXT NOT NULL UNIQUE,
    policy_id        UUID NOT NULL REFERENCES permission_policies(id),
    action           TEXT NOT NULL REFERENCES permission_actions(code),
    resource_pattern TEXT NOT NULL DEFAULT '*',
    effect           TEXT NOT NULL DEFAULT 'allow',
    condition_json   TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_permission_policy_rules_policy_id ON permission_policy_rules(policy_id);

CREATE TABLE permission_bindings (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_id             TEXT NOT NULL UNIQUE,
    tenant_id          UUID NOT NULL REFERENCES tenants(id),
    project_id         UUID REFERENCES projects(id),
    principal_type     TEXT NOT NULL,
    principal_user_id  UUID REFERENCES users(id),
    principal_group_id UUID REFERENCES groups(id),
    policy_id          UUID NOT NULL REFERENCES permission_policies(id),
    valid_from         TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to           TIMESTAMPTZ,
    created_by         UUID REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT permission_bindings_principal_chk
        CHECK ((principal_type = 'user' AND principal_user_id IS NOT NULL AND principal_group_id IS NULL)
            OR (principal_type = 'group' AND principal_user_id IS NULL AND principal_group_id IS NOT NULL)
            OR (principal_type = 'tenant_member' AND principal_user_id IS NULL AND principal_group_id IS NULL)),
    CONSTRAINT permission_bindings_project_tenant_chk
        CHECK (project_id IS NULL OR tenant_id IS NOT NULL)
);

CREATE INDEX idx_permission_bindings_tenant_id ON permission_bindings(tenant_id);
CREATE INDEX idx_permission_bindings_project_id ON permission_bindings(project_id) WHERE project_id IS NOT NULL;
CREATE INDEX idx_permission_bindings_principal_user_id
    ON permission_bindings(principal_user_id)
    WHERE principal_user_id IS NOT NULL;
CREATE INDEX idx_permission_bindings_principal_group_id
    ON permission_bindings(principal_group_id)
    WHERE principal_group_id IS NOT NULL;

INSERT INTO groups (id, api_id, tenant_id, code, name, description, builtin, created_at, updated_at)
SELECT
    gen_random_uuid(),
    'grp_' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 26)),
    t.id,
    'tenant-admin',
    'Tenant Admin',
    'Built-in tenant administrators.',
    true,
    now(),
    now()
FROM tenants t;

INSERT INTO group_members (id, api_id, group_id, user_id, status, created_at, updated_at)
SELECT
    gen_random_uuid(),
    'gmb_' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 26)),
    g.id,
    au.user_id,
    'active',
    now(),
    now()
FROM admin_users au
JOIN groups g ON g.tenant_id = au.tenant_id AND g.code = 'tenant-admin'
WHERE au.scope = 'tenant'
  AND au.status = 'active'
  AND au.tenant_id IS NOT NULL
  AND au.valid_to IS NULL;

INSERT INTO permission_policies (
    id, api_id, tenant_id, code, name, description, builtin, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    'pol_' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 26)),
    t.id,
    template.code,
    template.name,
    template.description,
    true,
    now(),
    now()
FROM tenants t
CROSS JOIN (
    VALUES
        ('tenant-admin', 'Tenant Admin', 'Full tenant management permissions.'),
        ('project-admin', 'Project Admin', 'Manage assigned projects.'),
        ('project-member', 'Project Member', 'Work inside assigned projects.'),
        ('project-viewer', 'Project Viewer', 'Read assigned projects.')
) AS template(code, name, description);

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
        ('tenant-admin', 'tenant.access', 'tenant:*'),
        ('tenant-admin', 'tenant.read', 'tenant:*'),
        ('tenant-admin', 'tenant.update', 'tenant:*'),
        ('tenant-admin', 'tenant.member.manage', 'tenant:*'),
        ('tenant-admin', 'project.create', 'project:*'),
        ('tenant-admin', 'project.read', 'project:*'),
        ('tenant-admin', 'project.update', 'project:*'),
        ('tenant-admin', 'project.delete', 'project:*'),
        ('tenant-admin', 'project.manage', 'project:*'),
        ('tenant-admin', 'permission.group.manage', 'permission:*'),
        ('tenant-admin', 'permission.assignment.manage', 'permission:*'),
        ('tenant-admin', 'permission.policy.manage', 'permission:*'),
        ('project-admin', 'project.read', 'project:*'),
        ('project-admin', 'project.update', 'project:*'),
        ('project-admin', 'project.delete', 'project:*'),
        ('project-admin', 'project.manage', 'project:*'),
        ('project-member', 'project.read', 'project:*'),
        ('project-member', 'project.update', 'project:*'),
        ('project-viewer', 'project.read', 'project:*')
) AS rule(policy_code, action, resource_pattern)
    ON rule.policy_code = pp.code;

INSERT INTO permission_bindings (
    id, api_id, tenant_id, project_id, principal_type, principal_user_id, principal_group_id,
    policy_id, valid_from, valid_to, created_by, created_at
)
SELECT
    gen_random_uuid(),
    'pbd_' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 26)),
    g.tenant_id,
    NULL,
    'group',
    NULL,
    g.id,
    pp.id,
    now(),
    NULL,
    NULL,
    now()
FROM groups g
JOIN permission_policies pp ON pp.tenant_id = g.tenant_id AND pp.code = 'tenant-admin'
WHERE g.code = 'tenant-admin';

INSERT INTO permission_policies (
    id, api_id, tenant_id, code, name, description, builtin, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    'pol_' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 26)),
    ag.tenant_id,
    'migrated-grant-' || ag.api_id,
    'Migrated grant ' || ag.api_id,
    'Backfilled from legacy access_grants.',
    false,
    now(),
    now()
FROM access_grants ag
WHERE ag.scope IN ('tenant', 'project')
  AND ag.tenant_id IS NOT NULL;

INSERT INTO permission_policy_rules (
    id, api_id, policy_id, action, resource_pattern, effect, condition_json, created_at
)
SELECT
    gen_random_uuid(),
    'prl_' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 26)),
    pp.id,
    ag.action,
    ag.resource_pattern,
    ag.effect,
    NULL,
    now()
FROM access_grants ag
JOIN permission_policies pp
    ON pp.code = 'migrated-grant-' || ag.api_id
WHERE ag.scope IN ('tenant', 'project')
  AND ag.tenant_id IS NOT NULL;

INSERT INTO permission_bindings (
    id, api_id, tenant_id, project_id, principal_type, principal_user_id, principal_group_id,
    policy_id, valid_from, valid_to, created_by, created_at
)
SELECT
    gen_random_uuid(),
    'pbd_' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 26)),
    ag.tenant_id,
    ag.project_id,
    'user',
    ag.subject_user_id,
    NULL,
    pp.id,
    ag.valid_from,
    ag.valid_to,
    ag.granted_by,
    now()
FROM access_grants ag
JOIN permission_policies pp
    ON pp.code = 'migrated-grant-' || ag.api_id
WHERE ag.scope IN ('tenant', 'project')
  AND ag.tenant_id IS NOT NULL;
