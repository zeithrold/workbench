-- Rename work item config permission actions to dotted lower-case form (no underscores).
INSERT INTO permission_actions (id, code, description)
VALUES
    (gen_random_uuid(), 'workitem.config.read', 'Read work item configuration.'),
    (gen_random_uuid(), 'workitem.config.manage', 'Manage work item configuration.')
ON CONFLICT (code) DO NOTHING;

UPDATE permission_policy_rules
SET action = 'workitem.config.read'
WHERE action = 'work_item_config.read';

UPDATE permission_policy_rules
SET action = 'workitem.config.manage'
WHERE action = 'work_item_config.manage';

UPDATE access_grants
SET action = 'workitem.config.read'
WHERE action = 'work_item_config.read';

UPDATE access_grants
SET action = 'workitem.config.manage'
WHERE action = 'work_item_config.manage';

DELETE FROM permission_actions
WHERE code IN ('work_item_config.read', 'work_item_config.manage');
