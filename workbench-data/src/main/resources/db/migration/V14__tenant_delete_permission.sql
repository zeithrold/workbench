INSERT INTO permission_actions (id, code, description)
VALUES (gen_random_uuid(), 'tenant.delete', 'Destroy tenants.')
ON CONFLICT (code) DO NOTHING;
