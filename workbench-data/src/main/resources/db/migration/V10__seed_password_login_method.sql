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
    'lmg_01JABCDEFGHJKMNPQRSTVWXYZ0',
    'password',
    'password',
    'Password',
    true,
    true,
    '{"credential":"password"}',
    now(),
    now()
)
ON CONFLICT (code) DO NOTHING;
