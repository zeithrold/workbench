CREATE TABLE tenant_config_entries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    config_key TEXT NOT NULL,
    value_json JSONB NOT NULL DEFAULT '{}',
    secret_ref TEXT,
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, config_key)
);

CREATE INDEX idx_tenant_config_entries_tenant_id
    ON tenant_config_entries(tenant_id);
