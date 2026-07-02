CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    timezone TEXT NOT NULL DEFAULT 'UTC',
    locale TEXT NOT NULL DEFAULT 'en-US',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    primary_email CITEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name TEXT NOT NULL,
    identifier TEXT NOT NULL,
    description TEXT,
    next_issue_sequence BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, identifier)
);

CREATE TABLE issues (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID NOT NULL REFERENCES projects(id),
    issue_type_api_id TEXT NOT NULL,
    issue_type_config_api_id TEXT NOT NULL,
    sequence_no BIGINT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    status_group TEXT NOT NULL,
    properties_snapshot JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, sequence_no)
);

CREATE TABLE permission_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE issue_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL,
    project_id UUID,
    issue_id UUID,
    actor_user_id UUID,
    event_type TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload JSONB NOT NULL DEFAULT '{}',
    request_id TEXT,
    trace_id TEXT
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id TEXT NOT NULL UNIQUE,
    tenant_id UUID,
    actor_user_id UUID,
    action TEXT NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id UUID,
    resource_api_id TEXT,
    result TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
