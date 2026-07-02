INSERT INTO permission_actions (id, code, description)
VALUES
    (gen_random_uuid(), 'tenant.access', 'Access a tenant scoped API.'),
    (gen_random_uuid(), 'project.create', 'Create projects in a tenant.'),
    (gen_random_uuid(), 'project.read', 'Read project data.'),
    (gen_random_uuid(), 'project.manage', 'Manage project settings.'),
    (gen_random_uuid(), 'permission.role.manage', 'Manage roles.'),
    (gen_random_uuid(), 'permission.policy.manage', 'Manage permission policies.'),
    (gen_random_uuid(), 'permission.assignment.manage', 'Manage role assignments.')
ON CONFLICT (code) DO NOTHING;
