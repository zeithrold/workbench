-- Empty condition_json objects are invalid for permission rules; treat as unconditional (NULL).
UPDATE permission_policy_rules
SET condition_json = NULL
WHERE condition_json IS NOT NULL
  AND trim(condition_json) IN ('{}', '');
