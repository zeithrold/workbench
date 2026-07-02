CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    session_hash TEXT NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    login_account_id UUID NOT NULL REFERENCES login_accounts(id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_sessions_user_active
    ON auth_sessions(user_id, expires_at, revoked_at);

CREATE TABLE bearer_tokens (
    id UUID PRIMARY KEY,
    token_hash TEXT NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    login_account_id UUID NOT NULL REFERENCES login_accounts(id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bearer_tokens_user_active
    ON bearer_tokens(user_id, expires_at, revoked_at);
