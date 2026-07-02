CREATE OR REPLACE FUNCTION workbench_generate_public_id(prefix TEXT)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
  alphabet TEXT := '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
  suffix TEXT := '';
  index INTEGER;
BEGIN
  FOR index IN 1..26 LOOP
    suffix := suffix || substr(alphabet, floor(random() * 32 + 1)::INTEGER, 1);
  END LOOP;
  RETURN prefix || '_' || suffix;
END;
$$;

ALTER TABLE login_method_definitions ADD COLUMN api_id TEXT;

UPDATE login_method_definitions
SET api_id = workbench_generate_public_id('lmg')
WHERE api_id IS NULL;

ALTER TABLE login_method_definitions
  ALTER COLUMN api_id SET NOT NULL;

ALTER TABLE login_method_definitions
  ADD CONSTRAINT login_method_definitions_api_id_key UNIQUE (api_id);

ALTER TABLE bearer_tokens ADD COLUMN api_id TEXT;

UPDATE bearer_tokens
SET api_id = workbench_generate_public_id('btk')
WHERE api_id IS NULL;

ALTER TABLE bearer_tokens
  ALTER COLUMN api_id SET NOT NULL;

ALTER TABLE bearer_tokens
  ADD CONSTRAINT bearer_tokens_api_id_key UNIQUE (api_id);

DROP FUNCTION workbench_generate_public_id(TEXT);
