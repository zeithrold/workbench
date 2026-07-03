INSERT INTO permission_actions (id, code, description)
VALUES
    (gen_random_uuid(), 'tenant.create', 'Create tenants.'),
    (gen_random_uuid(), 'tenant.read', 'Read tenant metadata.'),
    (gen_random_uuid(), 'tenant.update', 'Update tenant metadata.')
ON CONFLICT (code) DO NOTHING;
