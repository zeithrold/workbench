ALTER TABLE auth_sessions
    ADD COLUMN active_tenant_id UUID REFERENCES tenants(id);

CREATE INDEX idx_auth_sessions_active_tenant
    ON auth_sessions(active_tenant_id)
    WHERE active_tenant_id IS NOT NULL;
