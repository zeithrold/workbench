-- Canonicalize legacy flat system field names in transition condition JSON.
UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"actor"', '"field":"user.currentUser"')::jsonb
WHERE permission_condition::text LIKE '%"field":"actor"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"actorId"', '"field":"user.currentUser"')::jsonb
WHERE permission_condition::text LIKE '%"field":"actorId"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"reporter"', '"field":"issue.reporter"')::jsonb
WHERE permission_condition::text LIKE '%"field":"reporter"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"reporterId"', '"field":"issue.reporter"')::jsonb
WHERE permission_condition::text LIKE '%"field":"reporterId"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"assignee"', '"field":"issue.assignee"')::jsonb
WHERE permission_condition::text LIKE '%"field":"assignee"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"assigneeId"', '"field":"issue.assignee"')::jsonb
WHERE permission_condition::text LIKE '%"field":"assigneeId"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"status"', '"field":"issue.status"')::jsonb
WHERE permission_condition::text LIKE '%"field":"status"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"statusId"', '"field":"issue.status"')::jsonb
WHERE permission_condition::text LIKE '%"field":"statusId"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"statusGroup"', '"field":"issue.statusGroup"')::jsonb
WHERE permission_condition::text LIKE '%"field":"statusGroup"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"issueType"', '"field":"issue.issueType"')::jsonb
WHERE permission_condition::text LIKE '%"field":"issueType"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"issueTypeId"', '"field":"issue.issueType"')::jsonb
WHERE permission_condition::text LIKE '%"field":"issueTypeId"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"issueTypeConfig"', '"field":"issue.issueTypeConfig"')::jsonb
WHERE permission_condition::text LIKE '%"field":"issueTypeConfig"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"issueTypeConfigId"', '"field":"issue.issueTypeConfig"')::jsonb
WHERE permission_condition::text LIKE '%"field":"issueTypeConfigId"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"project"', '"field":"issue.project"')::jsonb
WHERE permission_condition::text LIKE '%"field":"project"%';

UPDATE workflow_transitions
SET permission_condition = replace(permission_condition::text, '"field":"projectId"', '"field":"issue.project"')::jsonb
WHERE permission_condition::text LIKE '%"field":"projectId"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"actor"', '"field":"user.currentUser"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"actor"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"actorId"', '"field":"user.currentUser"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"actorId"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"reporter"', '"field":"issue.reporter"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"reporter"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"reporterId"', '"field":"issue.reporter"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"reporterId"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"assignee"', '"field":"issue.assignee"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"assignee"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"assigneeId"', '"field":"issue.assignee"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"assigneeId"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"status"', '"field":"issue.status"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"status"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"statusId"', '"field":"issue.status"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"statusId"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"statusGroup"', '"field":"issue.statusGroup"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"statusGroup"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"issueType"', '"field":"issue.issueType"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"issueType"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"issueTypeId"', '"field":"issue.issueType"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"issueTypeId"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"issueTypeConfig"', '"field":"issue.issueTypeConfig"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"issueTypeConfig"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"issueTypeConfigId"', '"field":"issue.issueTypeConfig"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"issueTypeConfigId"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"project"', '"field":"issue.project"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"project"%';

UPDATE workflow_transitions
SET precondition_ast = replace(precondition_ast::text, '"field":"projectId"', '"field":"issue.project"')::jsonb
WHERE precondition_ast::text LIKE '%"field":"projectId"%';
