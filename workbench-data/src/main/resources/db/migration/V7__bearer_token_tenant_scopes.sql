ALTER TABLE bearer_tokens
    ADD COLUMN tenant_id UUID REFERENCES tenants(id),
    ADD COLUMN name TEXT,
    ADD COLUMN scopes JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN created_by UUID REFERENCES users(id);

CREATE INDEX idx_bearer_tokens_tenant_user_active
    ON bearer_tokens(tenant_id, user_id, expires_at, revoked_at);
