ALTER TABLE issue_types
    ADD CONSTRAINT chk_issue_types_scope_project
    CHECK (
        (scope = 'tenant' AND project_id IS NULL)
        OR (scope = 'project' AND project_id IS NOT NULL)
    );

ALTER TABLE issue_type_configs
    ADD CONSTRAINT chk_issue_type_configs_scope_project
    CHECK (
        (scope = 'tenant' AND project_id IS NULL)
        OR (scope = 'project' AND project_id IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_issue_types_tenant_code
    ON issue_types(tenant_id, code)
    WHERE scope = 'tenant' AND project_id IS NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_issue_types_project_code
    ON issue_types(tenant_id, project_id, code)
    WHERE scope = 'project' AND project_id IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_issue_type_config_one_initial_status
    ON issue_type_config_statuses(issue_type_config_id)
    WHERE is_initial = true;

CREATE UNIQUE INDEX uq_issue_type_configs_active_tenant_default
    ON issue_type_configs(tenant_id, issue_type_id)
    WHERE scope = 'tenant' AND project_id IS NULL AND is_active = true AND valid_to IS NULL;

CREATE UNIQUE INDEX uq_issue_type_configs_active_project_override
    ON issue_type_configs(tenant_id, project_id, issue_type_id)
    WHERE scope = 'project' AND project_id IS NOT NULL AND is_active = true AND valid_to IS NULL;
