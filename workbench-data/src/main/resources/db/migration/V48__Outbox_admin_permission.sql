INSERT INTO permission_actions (id, code, description)
VALUES
  (gen_random_uuid(), 'outbox.read', 'Read domain outbox messages.'),
  (gen_random_uuid(), 'outbox.manage', 'Manage domain outbox messages including replay.')
ON CONFLICT (code) DO NOTHING;
