ALTER TABLE tenants
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN archived_by UUID REFERENCES users(id),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by UUID REFERENCES users(id),
    ADD COLUMN delete_reason TEXT;

CREATE INDEX idx_tenants_deleted_at ON tenants(deleted_at);

ALTER TABLE users
    ADD COLUMN avatar_url TEXT,
    ADD COLUMN timezone TEXT,
    ADD COLUMN locale TEXT,
    ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN archived_by UUID REFERENCES users(id),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by UUID REFERENCES users(id),
    ADD COLUMN delete_reason TEXT;

CREATE INDEX idx_users_primary_email ON users(primary_email);

CREATE TABLE tenant_members (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES users(id),
    status TEXT NOT NULL DEFAULT 'active',
    joined_at TIMESTAMPTZ,
    invited_by UUID REFERENCES users(id),
    archived_at TIMESTAMPTZ,
    archived_by UUID REFERENCES users(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users(id),
    delete_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_tenant_members_tenant_status ON tenant_members(tenant_id, status);
CREATE INDEX idx_tenant_members_user_id ON tenant_members(user_id);

CREATE TABLE login_method_definitions (
    id UUID PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    kind TEXT NOT NULL,
    name TEXT NOT NULL,
    is_builtin BOOLEAN NOT NULL DEFAULT false,
    is_enabled_globally BOOLEAN NOT NULL DEFAULT true,
    config_schema JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tenant_login_method_settings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    login_method_id UUID NOT NULL REFERENCES login_method_definitions(id),
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    allow_signup BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 100,
    config JSONB NOT NULL DEFAULT '{}',
    secret_ref TEXT,
    created_by UUID REFERENCES users(id),
    updated_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, login_method_id)
);

CREATE INDEX idx_tenant_login_method_settings_enabled
    ON tenant_login_method_settings(tenant_id, is_enabled);

CREATE TABLE login_accounts (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    login_method_id UUID NOT NULL REFERENCES login_method_definitions(id),
    subject TEXT NOT NULL,
    normalized_subject TEXT NOT NULL,
    display_name TEXT,
    last_used_at TIMESTAMPTZ,
    disabled_at TIMESTAMPTZ,
    disabled_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (login_method_id, normalized_subject)
);

CREATE TABLE login_account_parameters (
    id UUID PRIMARY KEY,
    login_account_id UUID NOT NULL REFERENCES login_accounts(id),
    parameter_key TEXT NOT NULL,
    parameter_value TEXT,
    secret_ref TEXT,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (login_account_id, parameter_key)
);

CREATE TABLE user_login_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    login_account_id UUID NOT NULL UNIQUE REFERENCES login_accounts(id),
    linked_by UUID REFERENCES users(id),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unlinked_at TIMESTAMPTZ,
    UNIQUE (user_id, login_account_id)
);

ALTER TABLE projects
    ADD COLUMN lead_user_id UUID REFERENCES users(id),
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN archived_by UUID REFERENCES users(id),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by UUID REFERENCES users(id),
    ADD COLUMN delete_reason TEXT,
    ADD COLUMN created_by UUID REFERENCES users(id);

CREATE INDEX idx_projects_tenant_deleted_at ON projects(tenant_id, deleted_at);

CREATE TABLE project_identifier_aliases (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID NOT NULL REFERENCES projects(id),
    identifier TEXT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT false,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to TIMESTAMPTZ,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, identifier)
);

CREATE INDEX idx_project_identifier_aliases_project_current
    ON project_identifier_aliases(project_id, is_current);

CREATE TABLE issue_hierarchy_policies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID NOT NULL UNIQUE REFERENCES projects(id),
    max_depth INTEGER NOT NULL DEFAULT 3,
    allow_cross_project_children BOOLEAN NOT NULL DEFAULT false,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_issue_hierarchy_policies_tenant_project
    ON issue_hierarchy_policies(tenant_id, project_id);

CREATE TABLE priorities (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    rank INTEGER NOT NULL,
    color TEXT,
    icon TEXT,
    is_default BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_priorities_tenant_rank ON priorities(tenant_id, rank);

CREATE TABLE issue_statuses (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    status_group TEXT NOT NULL,
    rank INTEGER NOT NULL DEFAULT 100,
    color TEXT,
    is_terminal BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_issue_statuses_tenant_group ON issue_statuses(tenant_id, status_group);

CREATE TABLE property_definitions (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    data_type TEXT NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT false,
    is_array BOOLEAN NOT NULL DEFAULT false,
    validation_schema JSONB NOT NULL DEFAULT '{}',
    search_config JSONB NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_property_definitions_tenant_type ON property_definitions(tenant_id, data_type);

CREATE TABLE property_options (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    property_id UUID NOT NULL REFERENCES property_definitions(id),
    code TEXT NOT NULL,
    label TEXT NOT NULL,
    rank INTEGER NOT NULL DEFAULT 100,
    color TEXT,
    is_default BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (property_id, code)
);

CREATE INDEX idx_property_options_tenant_property_rank
    ON property_options(tenant_id, property_id, rank);

CREATE TABLE issue_types (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID REFERENCES projects(id),
    scope TEXT NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    icon TEXT,
    color TEXT,
    rank INTEGER NOT NULL DEFAULT 100,
    is_active BOOLEAN NOT NULL DEFAULT true,
    archived_at TIMESTAMPTZ,
    archived_by UUID REFERENCES users(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users(id),
    delete_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, project_id, code)
);

CREATE INDEX idx_issue_types_tenant_scope ON issue_types(tenant_id, scope);

CREATE TABLE workflows (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT true,
    published_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    archived_by UUID REFERENCES users(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users(id),
    delete_reason TEXT,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code, version)
);

CREATE INDEX idx_workflows_tenant_code_active ON workflows(tenant_id, code, is_active);

CREATE TABLE issue_type_configs (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    scope TEXT NOT NULL,
    project_id UUID REFERENCES projects(id),
    issue_type_id UUID NOT NULL REFERENCES issue_types(id),
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    version INTEGER NOT NULL DEFAULT 1,
    name_override TEXT,
    icon_override TEXT,
    color_override TEXT,
    rank INTEGER NOT NULL DEFAULT 100,
    is_active BOOLEAN NOT NULL DEFAULT true,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to TIMESTAMPTZ,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, scope, project_id, issue_type_id, version)
);

CREATE INDEX idx_issue_type_configs_type_scope
    ON issue_type_configs(tenant_id, issue_type_id, scope, project_id, valid_to);
CREATE INDEX idx_issue_type_configs_workflow ON issue_type_configs(tenant_id, workflow_id);

CREATE TABLE issue_type_config_statuses (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    issue_type_config_id UUID NOT NULL REFERENCES issue_type_configs(id),
    status_id UUID NOT NULL REFERENCES issue_statuses(id),
    is_initial BOOLEAN NOT NULL DEFAULT false,
    is_terminal BOOLEAN NOT NULL DEFAULT false,
    rank INTEGER NOT NULL DEFAULT 100,
    UNIQUE (issue_type_config_id, status_id)
);

CREATE INDEX idx_issue_type_config_statuses_tenant_config
    ON issue_type_config_statuses(tenant_id, issue_type_config_id);

CREATE TABLE issue_type_config_properties (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    issue_type_config_id UUID NOT NULL REFERENCES issue_type_configs(id),
    property_id UUID NOT NULL REFERENCES property_definitions(id),
    is_required BOOLEAN NOT NULL DEFAULT false,
    default_value JSONB,
    validation_override JSONB NOT NULL DEFAULT '{}',
    rank INTEGER NOT NULL DEFAULT 100,
    display_config JSONB NOT NULL DEFAULT '{}',
    UNIQUE (issue_type_config_id, property_id)
);

CREATE INDEX idx_issue_type_config_properties_tenant_config_rank
    ON issue_type_config_properties(tenant_id, issue_type_config_id, rank);

CREATE TABLE issue_subtype_constraints (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID REFERENCES projects(id),
    parent_issue_type_id UUID NOT NULL REFERENCES issue_types(id),
    child_issue_type_id UUID NOT NULL REFERENCES issue_types(id),
    is_default BOOLEAN NOT NULL DEFAULT false,
    min_children INTEGER,
    max_children INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, project_id, parent_issue_type_id, child_issue_type_id)
);

CREATE INDEX idx_issue_subtype_constraints_parent
    ON issue_subtype_constraints(tenant_id, project_id, parent_issue_type_id);
CREATE INDEX idx_issue_subtype_constraints_child
    ON issue_subtype_constraints(tenant_id, child_issue_type_id);

CREATE TABLE workflow_transitions (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    name TEXT NOT NULL,
    from_status_id UUID NOT NULL REFERENCES issue_statuses(id),
    to_status_id UUID NOT NULL REFERENCES issue_statuses(id),
    rank INTEGER NOT NULL DEFAULT 100,
    permission_condition JSONB NOT NULL DEFAULT '{}',
    precondition_ast JSONB NOT NULL DEFAULT '{}',
    required_properties JSONB NOT NULL DEFAULT '[]',
    optional_properties JSONB NOT NULL DEFAULT '[]',
    property_defaults JSONB NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workflow_transitions_status_pair
    ON workflow_transitions(workflow_id, from_status_id, to_status_id);
CREATE INDEX idx_workflow_transitions_tenant_workflow
    ON workflow_transitions(tenant_id, workflow_id);

CREATE TABLE sprints (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID NOT NULL REFERENCES projects(id),
    name TEXT NOT NULL,
    goal TEXT,
    status TEXT NOT NULL DEFAULT 'planned',
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_by UUID REFERENCES users(id),
    archived_at TIMESTAMPTZ,
    archived_by UUID REFERENCES users(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users(id),
    delete_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sprints_tenant_project_status ON sprints(tenant_id, project_id, status);

ALTER TABLE issues
    ADD COLUMN issue_type_id UUID NOT NULL REFERENCES issue_types(id),
    ADD COLUMN issue_type_config_id UUID NOT NULL REFERENCES issue_type_configs(id),
    ADD COLUMN status_id UUID NOT NULL REFERENCES issue_statuses(id),
    ADD COLUMN priority_id UUID REFERENCES priorities(id),
    ADD COLUMN reporter_id UUID NOT NULL REFERENCES users(id),
    ADD COLUMN assignee_id UUID REFERENCES users(id),
    ADD COLUMN sprint_id UUID REFERENCES sprints(id),
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN archived_by UUID REFERENCES users(id),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by UUID REFERENCES users(id),
    ADD COLUMN delete_reason TEXT,
    ADD COLUMN created_by UUID NOT NULL REFERENCES users(id),
    ADD COLUMN updated_by UUID REFERENCES users(id),
    DROP COLUMN issue_type_api_id,
    DROP COLUMN issue_type_config_api_id,
    DROP COLUMN status_group;

CREATE INDEX idx_issues_tenant_type_config ON issues(tenant_id, issue_type_config_id);
CREATE INDEX idx_issues_tenant_project_status ON issues(tenant_id, project_id, status_id);
CREATE INDEX idx_issues_tenant_assignee ON issues(tenant_id, assignee_id);
CREATE INDEX idx_issues_tenant_reporter ON issues(tenant_id, reporter_id);
CREATE INDEX idx_issues_tenant_sprint ON issues(tenant_id, sprint_id);
CREATE INDEX idx_issues_tenant_deleted_at ON issues(tenant_id, deleted_at);

CREATE TABLE issue_hierarchy (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID NOT NULL REFERENCES projects(id),
    parent_issue_id UUID NOT NULL REFERENCES issues(id),
    child_issue_id UUID NOT NULL UNIQUE REFERENCES issues(id),
    rank INTEGER NOT NULL DEFAULT 100,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (parent_issue_id, child_issue_id)
);

CREATE INDEX idx_issue_hierarchy_parent_rank ON issue_hierarchy(tenant_id, parent_issue_id, rank);
CREATE INDEX idx_issue_hierarchy_child ON issue_hierarchy(tenant_id, child_issue_id);
CREATE INDEX idx_issue_hierarchy_project ON issue_hierarchy(tenant_id, project_id);

CREATE TABLE issue_key_aliases (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID NOT NULL REFERENCES projects(id),
    issue_id UUID NOT NULL REFERENCES issues(id),
    issue_key TEXT NOT NULL,
    project_identifier TEXT NOT NULL,
    sequence_no BIGINT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID REFERENCES users(id),
    UNIQUE (tenant_id, issue_key)
);

CREATE INDEX idx_issue_key_aliases_issue_current ON issue_key_aliases(issue_id, is_current);
CREATE INDEX idx_issue_key_aliases_project_sequence ON issue_key_aliases(project_id, sequence_no);

CREATE TABLE issue_property_values (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    issue_id UUID NOT NULL REFERENCES issues(id),
    property_id UUID NOT NULL REFERENCES property_definitions(id),
    value_text TEXT,
    value_number NUMERIC,
    value_boolean BOOLEAN,
    value_date DATE,
    value_datetime TIMESTAMPTZ,
    value_json JSONB,
    value_user_id UUID REFERENCES users(id),
    value_project_id UUID REFERENCES projects(id),
    value_issue_id UUID REFERENCES issues(id),
    value_option_id UUID REFERENCES property_options(id),
    value_array JSONB,
    updated_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (issue_id, property_id)
);

CREATE INDEX idx_issue_property_values_tenant_property ON issue_property_values(tenant_id, property_id);
CREATE INDEX idx_issue_property_values_user ON issue_property_values(value_user_id);
CREATE INDEX idx_issue_property_values_option ON issue_property_values(value_option_id);

CREATE TABLE issue_status_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    issue_id UUID NOT NULL REFERENCES issues(id),
    from_status_id UUID REFERENCES issue_statuses(id),
    to_status_id UUID NOT NULL REFERENCES issue_statuses(id),
    transition_id UUID REFERENCES workflow_transitions(id),
    actor_user_id UUID REFERENCES users(id),
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_issue_status_history_issue_changed
    ON issue_status_history(tenant_id, issue_id, changed_at);
CREATE INDEX idx_issue_status_history_status_changed
    ON issue_status_history(tenant_id, to_status_id, changed_at);

CREATE TABLE issue_sprint_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    issue_id UUID NOT NULL REFERENCES issues(id),
    sprint_id UUID NOT NULL REFERENCES sprints(id),
    added_by UUID REFERENCES users(id),
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_by UUID REFERENCES users(id),
    removed_at TIMESTAMPTZ
);

CREATE INDEX idx_issue_sprint_history_issue_sprint
    ON issue_sprint_history(tenant_id, issue_id, sprint_id);
CREATE INDEX idx_issue_sprint_history_sprint ON issue_sprint_history(tenant_id, sprint_id);

CREATE TABLE issue_comments (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    issue_id UUID NOT NULL REFERENCES issues(id),
    author_id UUID NOT NULL REFERENCES users(id),
    body TEXT NOT NULL,
    body_format TEXT NOT NULL DEFAULT 'markdown',
    edited_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    archived_by UUID REFERENCES users(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users(id),
    delete_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_issue_comments_issue_created ON issue_comments(tenant_id, issue_id, created_at);

CREATE TABLE attachments (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    issue_id UUID REFERENCES issues(id),
    comment_id UUID REFERENCES issue_comments(id),
    uploaded_by UUID NOT NULL REFERENCES users(id),
    filename TEXT NOT NULL,
    content_type TEXT,
    byte_size BIGINT NOT NULL,
    checksum TEXT,
    storage_key TEXT NOT NULL UNIQUE,
    metadata JSONB NOT NULL DEFAULT '{}',
    archived_at TIMESTAMPTZ,
    archived_by UUID REFERENCES users(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users(id),
    delete_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachments_issue ON attachments(tenant_id, issue_id);
CREATE INDEX idx_attachments_comment ON attachments(tenant_id, comment_id);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID REFERENCES tenants(id),
    scope TEXT NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    is_builtin BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, scope, code)
);

CREATE TABLE permission_policies (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    action_id UUID NOT NULL REFERENCES permission_actions(id),
    effect TEXT NOT NULL DEFAULT 'allow',
    resource_pattern TEXT NOT NULL,
    condition_ast JSONB NOT NULL DEFAULT '{}',
    version INTEGER NOT NULL DEFAULT 1,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to TIMESTAMPTZ,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, role_id, action_id, version)
);

CREATE INDEX idx_permission_policies_valid_to ON permission_policies(tenant_id, valid_to);

CREATE TABLE role_assignments (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    project_id UUID REFERENCES projects(id),
    granted_by UUID REFERENCES users(id),
    valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_role_assignments_user_role
    ON role_assignments(tenant_id, user_id, role_id, project_id, valid_to);
CREATE INDEX idx_role_assignments_project_role ON role_assignments(tenant_id, project_id, role_id);

CREATE TABLE saved_filters (
    id UUID PRIMARY KEY,
    api_id TEXT NOT NULL UNIQUE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    project_id UUID REFERENCES projects(id),
    owner_id UUID NOT NULL REFERENCES users(id),
    name TEXT NOT NULL,
    description TEXT,
    filter_ast JSONB NOT NULL,
    sort_ast JSONB NOT NULL DEFAULT '[]',
    is_shared BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_saved_filters_owner ON saved_filters(tenant_id, owner_id);
CREATE INDEX idx_saved_filters_project_shared ON saved_filters(tenant_id, project_id, is_shared);

CREATE INDEX idx_issue_events_issue_occurred ON issue_events(tenant_id, issue_id, occurred_at);
CREATE INDEX idx_issue_events_type_occurred ON issue_events(tenant_id, event_type, occurred_at);

ALTER TABLE audit_logs
    ADD COLUMN actor_login_account_id UUID,
    ADD COLUMN reason TEXT,
    ADD COLUMN before_snapshot JSONB,
    ADD COLUMN after_snapshot JSONB,
    ADD COLUMN ip_address INET,
    ADD COLUMN user_agent TEXT,
    ADD COLUMN request_id TEXT,
    ADD COLUMN trace_id TEXT;

CREATE INDEX idx_audit_logs_action_occurred ON audit_logs(tenant_id, action, occurred_at);
CREATE INDEX idx_audit_logs_resource ON audit_logs(tenant_id, resource_type, resource_id);
CREATE INDEX idx_audit_logs_actor_user_id ON audit_logs(actor_user_id);

CREATE TABLE auth_events (
    id UUID PRIMARY KEY,
    auth_event_id TEXT NOT NULL UNIQUE,
    tenant_id UUID,
    user_id UUID,
    login_account_id UUID,
    login_method_id UUID,
    event_type TEXT NOT NULL,
    result TEXT NOT NULL,
    failure_reason TEXT,
    ip_address INET,
    user_agent TEXT,
    metadata JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_events_type_occurred ON auth_events(tenant_id, event_type, occurred_at);
CREATE INDEX idx_auth_events_user_id ON auth_events(user_id);
CREATE INDEX idx_auth_events_login_account_id ON auth_events(login_account_id);
