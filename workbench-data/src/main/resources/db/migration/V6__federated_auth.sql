CREATE TABLE auth_login_states (
    id UUID PRIMARY KEY,
    state_hash TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    login_method_id UUID NOT NULL REFERENCES login_method_definitions(id),
    redirect_uri TEXT NOT NULL,
    pkce_verifier TEXT,
    return_url TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_login_states_expires
    ON auth_login_states(expires_at)
    WHERE consumed_at IS NULL;

CREATE TABLE magic_link_tokens (
    id UUID PRIMARY KEY,
    token_hash TEXT NOT NULL UNIQUE,
    login_method_id UUID NOT NULL REFERENCES login_method_definitions(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    normalized_subject TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_magic_link_tokens_subject_active
    ON magic_link_tokens(normalized_subject, expires_at)
    WHERE consumed_at IS NULL;
