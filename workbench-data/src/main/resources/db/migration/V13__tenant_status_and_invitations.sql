ALTER TABLE tenants
    ADD COLUMN status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'pending_activation'));

CREATE INDEX idx_tenants_status ON tenants(status) WHERE deleted_at IS NULL;

CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    invitation_type TEXT NOT NULL
        CHECK (invitation_type IN ('tenant_admin', 'tenant_member')),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    email TEXT NOT NULL,
    normalized_email TEXT NOT NULL,
    display_name TEXT,
    token_hash TEXT NOT NULL UNIQUE,
    invited_by UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invitations_tenant_type_active
    ON invitations(tenant_id, invitation_type)
    WHERE consumed_at IS NULL;
