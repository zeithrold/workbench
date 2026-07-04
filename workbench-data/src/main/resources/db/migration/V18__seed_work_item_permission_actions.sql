INSERT INTO permission_actions (id, code, description)
VALUES
    (gen_random_uuid(), 'issue.view', 'View work items.'),
    (gen_random_uuid(), 'issue.create', 'Create work items.'),
    (gen_random_uuid(), 'issue.update', 'Update work items.'),
    (gen_random_uuid(), 'issue.delete', 'Delete work items.'),
    (gen_random_uuid(), 'issue.assign', 'Assign work items.'),
    (gen_random_uuid(), 'issue.transition', 'Transition work items.'),
    (gen_random_uuid(), 'issue.comment.create', 'Create work item comments.'),
    (gen_random_uuid(), 'issue.comment.delete', 'Delete work item comments.'),
    (gen_random_uuid(), 'work_item_config.read', 'Read work item configuration.'),
    (gen_random_uuid(), 'work_item_config.manage', 'Manage work item configuration.'),
    (gen_random_uuid(), 'workflow.manage', 'Manage workflows.')
ON CONFLICT (code) DO NOTHING;
