ALTER TABLE permission_policy_rules ADD COLUMN position INTEGER;

WITH ranked AS (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY policy_id ORDER BY created_at, id) - 1 AS position
  FROM permission_policy_rules
)
UPDATE permission_policy_rules rules
SET position = ranked.position
FROM ranked
WHERE rules.id = ranked.id;

ALTER TABLE permission_policy_rules ALTER COLUMN position SET NOT NULL;
CREATE INDEX idx_permission_policy_rules_policy_position
  ON permission_policy_rules (policy_id, position);
