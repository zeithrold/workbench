--
-- PostgreSQL database dump
--


-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: citext; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS citext WITH SCHEMA public;


--
-- Name: EXTENSION citext; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION citext IS 'data type for case-insensitive character strings';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: access_grants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.access_grants (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    api_id text NOT NULL,
    scope text NOT NULL,
    tenant_id uuid,
    project_id uuid,
    subject_user_id uuid NOT NULL,
    action text NOT NULL,
    resource_pattern text DEFAULT '*'::text NOT NULL,
    effect text DEFAULT 'allow'::text NOT NULL,
    valid_from timestamp with time zone DEFAULT now() NOT NULL,
    valid_to timestamp with time zone,
    granted_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT access_grants_scope_chk CHECK ((((scope = 'instance'::text) AND (tenant_id IS NULL) AND (project_id IS NULL)) OR ((scope = 'tenant'::text) AND (tenant_id IS NOT NULL) AND (project_id IS NULL)) OR ((scope = 'project'::text) AND (tenant_id IS NOT NULL) AND (project_id IS NOT NULL))))
);


--
-- Name: admin_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    api_id text NOT NULL,
    user_id uuid NOT NULL,
    scope text NOT NULL,
    tenant_id uuid,
    status text DEFAULT 'active'::text NOT NULL,
    granted_by uuid,
    valid_from timestamp with time zone DEFAULT now() NOT NULL,
    valid_to timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT admin_users_scope_tenant_chk CHECK ((((scope = 'instance'::text) AND (tenant_id IS NULL)) OR ((scope = 'tenant'::text) AND (tenant_id IS NOT NULL))))
);


--
-- Name: attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attachments (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    issue_id uuid,
    comment_id uuid,
    uploaded_by uuid NOT NULL,
    filename text NOT NULL,
    content_type text,
    byte_size bigint NOT NULL,
    checksum text,
    storage_key text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    archived_at timestamp with time zone,
    archived_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    delete_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    purpose text DEFAULT 'standalone'::text NOT NULL,
    upload_status text DEFAULT 'completed'::text NOT NULL
);


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    audit_id text NOT NULL,
    tenant_id uuid,
    actor_user_id uuid,
    action text NOT NULL,
    resource_type text NOT NULL,
    resource_id uuid,
    resource_api_id text,
    result text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    actor_login_account_id uuid,
    reason text,
    before_snapshot jsonb,
    after_snapshot jsonb,
    ip_address inet,
    user_agent text,
    request_id text,
    trace_id text
);


--
-- Name: auth_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_events (
    id uuid NOT NULL,
    auth_event_id text NOT NULL,
    tenant_id uuid,
    user_id uuid,
    login_account_id uuid,
    login_method_id uuid,
    event_type text NOT NULL,
    result text NOT NULL,
    failure_reason text,
    ip_address inet,
    user_agent text,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: auth_login_states; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_login_states (
    id uuid NOT NULL,
    state_hash text NOT NULL,
    tenant_id uuid NOT NULL,
    login_method_id uuid NOT NULL,
    redirect_uri text NOT NULL,
    pkce_verifier text,
    return_url text,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: auth_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_sessions (
    id uuid NOT NULL,
    session_hash text NOT NULL,
    user_id uuid NOT NULL,
    login_account_id uuid NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    last_used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    active_tenant_id uuid
);


--
-- Name: bearer_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bearer_tokens (
    id uuid NOT NULL,
    token_hash text NOT NULL,
    user_id uuid NOT NULL,
    login_account_id uuid NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    last_used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    tenant_id uuid,
    name text,
    scopes jsonb DEFAULT '[]'::jsonb NOT NULL,
    created_by uuid,
    api_id text NOT NULL
);


--
-- Name: domain_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.domain_outbox (
    id uuid NOT NULL,
    topic text NOT NULL,
    partition_key text NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published_at timestamp with time zone,
    attempts integer DEFAULT 0 NOT NULL,
    event_id text NOT NULL,
    event_type text NOT NULL,
    event_version integer DEFAULT 1 NOT NULL,
    tenant_id text,
    status text DEFAULT 'PENDING'::text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    locked_until timestamp with time zone,
    last_error text
);


--
-- Name: group_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.group_members (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    api_id text NOT NULL,
    group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    status text DEFAULT 'active'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.groups (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    description text,
    builtin boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: invitations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invitations (
    id uuid NOT NULL,
    api_id text NOT NULL,
    invitation_type text NOT NULL,
    tenant_id uuid NOT NULL,
    email text NOT NULL,
    normalized_email text NOT NULL,
    display_name text,
    token_hash text NOT NULL,
    invited_by uuid NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT invitations_invitation_type_check CHECK ((invitation_type = ANY (ARRAY['tenant_admin'::text, 'tenant_member'::text])))
);


--
-- Name: issue_comments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_comments (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    issue_id uuid NOT NULL,
    author_id uuid NOT NULL,
    body_document jsonb NOT NULL,
    edited_at timestamp with time zone,
    archived_at timestamp with time zone,
    archived_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    delete_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    transition_id uuid,
    status_history_id uuid,
    body_plain_text text
);


--
-- Name: issue_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_id text NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid,
    issue_id uuid,
    actor_user_id uuid,
    event_type text NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    request_id text,
    trace_id text
);


--
-- Name: issue_hierarchy; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_hierarchy (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid NOT NULL,
    parent_issue_id uuid NOT NULL,
    child_issue_id uuid NOT NULL,
    rank integer DEFAULT 100 NOT NULL,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: issue_hierarchy_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_hierarchy_policies (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid NOT NULL,
    max_depth integer DEFAULT 3 NOT NULL,
    allow_cross_project_children boolean DEFAULT false NOT NULL,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: issue_key_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_key_aliases (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid NOT NULL,
    issue_id uuid NOT NULL,
    issue_key text NOT NULL,
    project_identifier text NOT NULL,
    sequence_no bigint NOT NULL,
    is_current boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid
);


--
-- Name: issue_property_values; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_property_values (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    issue_id uuid NOT NULL,
    property_id uuid NOT NULL,
    value_text text,
    value_number numeric,
    value_boolean boolean,
    value_date date,
    value_datetime timestamp with time zone,
    value_json jsonb,
    value_user_id uuid,
    value_project_id uuid,
    value_issue_id uuid,
    value_option_id uuid,
    value_array jsonb,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: issue_sprint_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_sprint_history (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    issue_id uuid NOT NULL,
    sprint_id uuid NOT NULL,
    added_by uuid,
    added_at timestamp with time zone DEFAULT now() NOT NULL,
    removed_by uuid,
    removed_at timestamp with time zone
);


--
-- Name: issue_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_status_history (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    issue_id uuid NOT NULL,
    from_status_id uuid,
    to_status_id uuid NOT NULL,
    transition_id uuid,
    actor_user_id uuid,
    started_at timestamp with time zone,
    ended_at timestamp with time zone,
    changed_at timestamp with time zone DEFAULT now() NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL
);


--
-- Name: issue_statuses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_statuses (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    status_group text NOT NULL,
    rank integer DEFAULT 100 NOT NULL,
    color text,
    is_terminal boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: issue_subtype_constraints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_subtype_constraints (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid,
    parent_issue_type_id uuid NOT NULL,
    child_issue_type_id uuid NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    min_children integer,
    max_children integer,
    is_active boolean DEFAULT true NOT NULL,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: issue_type_config_access_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_type_config_access_rules (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    issue_type_config_id uuid NOT NULL,
    subject_type text NOT NULL,
    subject_user_id uuid,
    subject_group_id uuid,
    subject_role_code text,
    action_type text NOT NULL,
    transition_id uuid,
    field_key text,
    effect text NOT NULL,
    condition_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    rank integer DEFAULT 100 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: issue_type_config_properties; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_type_config_properties (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    issue_type_config_id uuid NOT NULL,
    property_id uuid NOT NULL,
    validation_override jsonb DEFAULT '{}'::jsonb NOT NULL,
    rank integer DEFAULT 100 NOT NULL,
    display_config jsonb DEFAULT '{}'::jsonb NOT NULL
);


--
-- Name: issue_type_config_statuses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_type_config_statuses (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    issue_type_config_id uuid NOT NULL,
    status_id uuid NOT NULL,
    is_initial boolean DEFAULT false NOT NULL,
    is_terminal boolean DEFAULT false NOT NULL,
    rank integer DEFAULT 100 NOT NULL
);


--
-- Name: issue_type_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_type_configs (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    scope text NOT NULL,
    project_id uuid,
    issue_type_id uuid NOT NULL,
    workflow_id uuid NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    name_override text,
    icon_override text,
    color_override text,
    rank integer DEFAULT 100 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    valid_from timestamp with time zone DEFAULT now() NOT NULL,
    valid_to timestamp with time zone,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    create_fields jsonb DEFAULT '{"fields": {"title": {"participation": "required"}}, "target": "create", "version": 1, "resource": "work_item"}'::jsonb NOT NULL,
    CONSTRAINT chk_issue_type_configs_scope_project CHECK ((((scope = 'tenant'::text) AND (project_id IS NULL)) OR ((scope = 'project'::text) AND (project_id IS NOT NULL))))
);


--
-- Name: issue_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issue_types (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid,
    scope text NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    description text,
    icon text,
    color text,
    rank integer DEFAULT 100 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    archived_at timestamp with time zone,
    archived_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    delete_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_issue_types_scope_project CHECK ((((scope = 'tenant'::text) AND (project_id IS NULL)) OR ((scope = 'project'::text) AND (project_id IS NOT NULL))))
);


--
-- Name: issues; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.issues (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid NOT NULL,
    sequence_no bigint NOT NULL,
    title text NOT NULL,
    description_document jsonb,
    properties_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    issue_type_id uuid NOT NULL,
    issue_type_config_id uuid NOT NULL,
    status_id uuid NOT NULL,
    priority_id uuid,
    reporter_id uuid NOT NULL,
    assignee_id uuid,
    sprint_id uuid,
    archived_at timestamp with time zone,
    archived_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    delete_reason text,
    created_by uuid NOT NULL,
    updated_by uuid,
    description_plain_text text
);


--
-- Name: login_account_parameters; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_account_parameters (
    id uuid NOT NULL,
    login_account_id uuid NOT NULL,
    parameter_key text NOT NULL,
    parameter_value text,
    secret_ref text,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: login_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_accounts (
    id uuid NOT NULL,
    api_id text NOT NULL,
    login_method_id uuid NOT NULL,
    subject text NOT NULL,
    normalized_subject text NOT NULL,
    display_name text,
    last_used_at timestamp with time zone,
    disabled_at timestamp with time zone,
    disabled_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: login_method_definitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_method_definitions (
    id uuid NOT NULL,
    code text NOT NULL,
    kind text NOT NULL,
    name text NOT NULL,
    is_builtin boolean DEFAULT false NOT NULL,
    is_enabled_globally boolean DEFAULT true NOT NULL,
    config_schema jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    api_id text NOT NULL
);


--
-- Name: magic_link_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.magic_link_tokens (
    id uuid NOT NULL,
    token_hash text NOT NULL,
    login_method_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    normalized_subject text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: notification_deliveries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_deliveries (
    id uuid NOT NULL,
    notification_id uuid NOT NULL,
    channel text NOT NULL,
    status text NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    sent_at timestamp with time zone,
    last_error text
);


--
-- Name: notification_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_preferences (
    user_id uuid NOT NULL,
    notification_type text NOT NULL,
    in_app_enabled boolean DEFAULT true NOT NULL,
    email_enabled boolean DEFAULT true NOT NULL
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    api_id text NOT NULL,
    recipient_user_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid,
    work_item_id uuid,
    source_event_id text NOT NULL,
    notification_type text NOT NULL,
    title text NOT NULL,
    body text NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    read_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: permission_actions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission_actions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code text NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: permission_bindings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission_bindings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid,
    principal_type text NOT NULL,
    principal_user_id uuid,
    principal_group_id uuid,
    policy_id uuid NOT NULL,
    valid_from timestamp with time zone DEFAULT now() NOT NULL,
    valid_to timestamp with time zone,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT permission_bindings_principal_chk CHECK ((((principal_type = 'user'::text) AND (principal_user_id IS NOT NULL) AND (principal_group_id IS NULL)) OR ((principal_type = 'group'::text) AND (principal_user_id IS NULL) AND (principal_group_id IS NOT NULL)) OR ((principal_type = 'tenant_member'::text) AND (principal_user_id IS NULL) AND (principal_group_id IS NULL)))),
    CONSTRAINT permission_bindings_project_tenant_chk CHECK (((project_id IS NULL) OR (tenant_id IS NOT NULL)))
);


--
-- Name: permission_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission_policies (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    description text,
    builtin boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: permission_policy_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission_policy_rules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    api_id text NOT NULL,
    policy_id uuid NOT NULL,
    action text NOT NULL,
    resource_pattern text DEFAULT '*'::text NOT NULL,
    effect text DEFAULT 'allow'::text NOT NULL,
    condition_json text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: priorities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.priorities (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    rank integer NOT NULL,
    color text,
    icon text,
    is_default boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: processed_domain_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.processed_domain_events (
    consumer_name text NOT NULL,
    event_id text NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: project_identifier_aliases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_identifier_aliases (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid NOT NULL,
    identifier text NOT NULL,
    is_current boolean DEFAULT false NOT NULL,
    valid_from timestamp with time zone DEFAULT now() NOT NULL,
    valid_to timestamp with time zone,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projects (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    name text NOT NULL,
    identifier text NOT NULL,
    description text,
    next_issue_sequence bigint DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    lead_user_id uuid,
    archived_at timestamp with time zone,
    archived_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    delete_reason text,
    created_by uuid,
    status text DEFAULT 'active'::text NOT NULL,
    non_member_visibility text DEFAULT 'invisible'::text NOT NULL,
    non_member_join_policy text DEFAULT 'admin_only'::text NOT NULL,
    CONSTRAINT projects_non_member_join_policy_chk CHECK ((non_member_join_policy = ANY (ARRAY['open'::text, 'admin_only'::text]))),
    CONSTRAINT projects_non_member_visibility_chk CHECK ((non_member_visibility = ANY (ARRAY['invisible'::text, 'read_only'::text, 'read_write'::text]))),
    CONSTRAINT projects_status_chk CHECK ((status = ANY (ARRAY['active'::text, 'archived'::text, 'destroying'::text])))
);


--
-- Name: property_definitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_definitions (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    description text,
    data_type text NOT NULL,
    is_system boolean DEFAULT false NOT NULL,
    is_array boolean DEFAULT false NOT NULL,
    validation_schema jsonb DEFAULT '{}'::jsonb NOT NULL,
    search_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: property_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_options (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    property_id uuid NOT NULL,
    code text NOT NULL,
    label text NOT NULL,
    rank integer DEFAULT 100 NOT NULL,
    color text,
    is_default boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: sprint_close_operations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sprint_close_operations (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid NOT NULL,
    sprint_id uuid NOT NULL,
    target_sprint_id uuid,
    disposition text NOT NULL,
    requested_by uuid NOT NULL,
    status text NOT NULL,
    total_items integer DEFAULT 0 NOT NULL,
    processed_items integer DEFAULT 0 NOT NULL,
    failed_items integer DEFAULT 0 NOT NULL,
    last_error text,
    idempotency_key text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    CONSTRAINT sprint_close_operations_disposition_check CHECK ((disposition = ANY (ARRAY['BACKLOG'::text, 'NEXT_SPRINT'::text, 'KEEP'::text]))),
    CONSTRAINT sprint_close_operations_status_check CHECK ((status = ANY (ARRAY['QUEUED'::text, 'RUNNING'::text, 'SUCCEEDED'::text, 'FAILED'::text])))
);


--
-- Name: sprints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sprints (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid NOT NULL,
    name text NOT NULL,
    goal text,
    status text DEFAULT 'planned'::text NOT NULL,
    start_at timestamp with time zone,
    end_at timestamp with time zone,
    closed_at timestamp with time zone,
    created_by uuid,
    archived_at timestamp with time zone,
    archived_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    delete_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tenant_config_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_config_entries (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    config_key text NOT NULL,
    value_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    secret_ref text,
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tenant_login_method_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_login_method_settings (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    login_method_id uuid NOT NULL,
    is_enabled boolean DEFAULT true NOT NULL,
    allow_signup boolean DEFAULT false NOT NULL,
    display_order integer DEFAULT 100 NOT NULL,
    config jsonb DEFAULT '{}'::jsonb NOT NULL,
    secret_ref text,
    created_by uuid,
    updated_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tenant_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_members (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    status text DEFAULT 'active'::text NOT NULL,
    joined_at timestamp with time zone,
    invited_by uuid,
    archived_at timestamp with time zone,
    archived_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    delete_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tenants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenants (
    id uuid NOT NULL,
    api_id text NOT NULL,
    name text NOT NULL,
    slug text NOT NULL,
    timezone text DEFAULT 'UTC'::text NOT NULL,
    locale text DEFAULT 'en-US'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    archived_at timestamp with time zone,
    archived_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    delete_reason text,
    status text DEFAULT 'active'::text NOT NULL,
    CONSTRAINT tenants_status_chk CHECK ((status = ANY (ARRAY['active'::text, 'pending_activation'::text, 'destroying'::text])))
);


--
-- Name: user_login_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_login_accounts (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    login_account_id uuid NOT NULL,
    linked_by uuid,
    linked_at timestamp with time zone DEFAULT now() NOT NULL,
    unlinked_at timestamp with time zone
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    api_id text NOT NULL,
    display_name text NOT NULL,
    primary_email public.citext,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    avatar_url text,
    timezone text,
    locale text,
    archived_at timestamp with time zone,
    archived_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    delete_reason text
);


--
-- Name: work_item_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_item_events (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid NOT NULL,
    work_item_id uuid NOT NULL,
    sequence bigint NOT NULL,
    event_type text NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    actor_user_id uuid,
    summary text,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    source_type text DEFAULT 'user'::text NOT NULL,
    source_id text,
    correlation_id text,
    request_id text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: work_item_timeline_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_item_timeline_entries (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    project_id uuid NOT NULL,
    work_item_id uuid NOT NULL,
    event_id uuid NOT NULL,
    sequence bigint NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: work_item_views; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_item_views (
    id uuid CONSTRAINT saved_filters_id_not_null NOT NULL,
    api_id text CONSTRAINT saved_filters_api_id_not_null NOT NULL,
    tenant_id uuid CONSTRAINT saved_filters_tenant_id_not_null NOT NULL,
    project_id uuid,
    owner_id uuid CONSTRAINT saved_filters_owner_id_not_null NOT NULL,
    name text CONSTRAINT saved_filters_name_not_null NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT saved_filters_created_at_not_null NOT NULL,
    updated_at timestamp with time zone DEFAULT now() CONSTRAINT saved_filters_updated_at_not_null NOT NULL,
    visibility text DEFAULT 'private'::text NOT NULL,
    display_fields jsonb DEFAULT '[]'::jsonb NOT NULL,
    query_ast jsonb DEFAULT '{"version": 1, "resource": "work_item"}'::jsonb NOT NULL,
    CONSTRAINT chk_work_item_views_visibility CHECK ((visibility = ANY (ARRAY['private'::text, 'project'::text, 'tenant'::text])))
);


--
-- Name: workflow_transitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_transitions (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    workflow_id uuid NOT NULL,
    name text NOT NULL,
    from_status_id uuid,
    to_status_id uuid NOT NULL,
    rank integer DEFAULT 100 NOT NULL,
    precondition_ast jsonb DEFAULT '{}'::jsonb NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    fields jsonb DEFAULT '{"fields": {}, "target": "transition", "version": 1, "resource": "work_item"}'::jsonb NOT NULL
);


--
-- Name: workflows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflows (
    id uuid NOT NULL,
    api_id text NOT NULL,
    tenant_id uuid NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    description text,
    version integer DEFAULT 1 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    published_at timestamp with time zone,
    archived_at timestamp with time zone,
    archived_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    delete_reason text,
    created_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: access_grants access_grants_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.access_grants
    ADD CONSTRAINT access_grants_api_id_key UNIQUE (api_id);


--
-- Name: access_grants access_grants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.access_grants
    ADD CONSTRAINT access_grants_pkey PRIMARY KEY (id);


--
-- Name: admin_users admin_users_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT admin_users_api_id_key UNIQUE (api_id);


--
-- Name: admin_users admin_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT admin_users_pkey PRIMARY KEY (id);


--
-- Name: attachments attachments_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments
    ADD CONSTRAINT attachments_api_id_key UNIQUE (api_id);


--
-- Name: attachments attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments
    ADD CONSTRAINT attachments_pkey PRIMARY KEY (id);


--
-- Name: attachments attachments_storage_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments
    ADD CONSTRAINT attachments_storage_key_key UNIQUE (storage_key);


--
-- Name: audit_logs audit_logs_audit_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_audit_id_key UNIQUE (audit_id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: auth_events auth_events_auth_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_events
    ADD CONSTRAINT auth_events_auth_event_id_key UNIQUE (auth_event_id);


--
-- Name: auth_events auth_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_events
    ADD CONSTRAINT auth_events_pkey PRIMARY KEY (id);


--
-- Name: auth_login_states auth_login_states_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_login_states
    ADD CONSTRAINT auth_login_states_pkey PRIMARY KEY (id);


--
-- Name: auth_login_states auth_login_states_state_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_login_states
    ADD CONSTRAINT auth_login_states_state_hash_key UNIQUE (state_hash);


--
-- Name: auth_sessions auth_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT auth_sessions_pkey PRIMARY KEY (id);


--
-- Name: auth_sessions auth_sessions_session_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT auth_sessions_session_hash_key UNIQUE (session_hash);


--
-- Name: bearer_tokens bearer_tokens_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bearer_tokens
    ADD CONSTRAINT bearer_tokens_api_id_key UNIQUE (api_id);


--
-- Name: bearer_tokens bearer_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bearer_tokens
    ADD CONSTRAINT bearer_tokens_pkey PRIMARY KEY (id);


--
-- Name: bearer_tokens bearer_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bearer_tokens
    ADD CONSTRAINT bearer_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: domain_outbox domain_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domain_outbox
    ADD CONSTRAINT domain_outbox_pkey PRIMARY KEY (id);


--
-- Name: group_members group_members_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.group_members
    ADD CONSTRAINT group_members_api_id_key UNIQUE (api_id);


--
-- Name: group_members group_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.group_members
    ADD CONSTRAINT group_members_pkey PRIMARY KEY (id);


--
-- Name: groups groups_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT groups_api_id_key UNIQUE (api_id);


--
-- Name: groups groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT groups_pkey PRIMARY KEY (id);


--
-- Name: groups groups_tenant_code_uniq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT groups_tenant_code_uniq UNIQUE (tenant_id, code);


--
-- Name: invitations invitations_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invitations
    ADD CONSTRAINT invitations_api_id_key UNIQUE (api_id);


--
-- Name: invitations invitations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invitations
    ADD CONSTRAINT invitations_pkey PRIMARY KEY (id);


--
-- Name: invitations invitations_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invitations
    ADD CONSTRAINT invitations_token_hash_key UNIQUE (token_hash);


--
-- Name: issue_comments issue_comments_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_comments
    ADD CONSTRAINT issue_comments_api_id_key UNIQUE (api_id);


--
-- Name: issue_comments issue_comments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_comments
    ADD CONSTRAINT issue_comments_pkey PRIMARY KEY (id);


--
-- Name: issue_events issue_events_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_events
    ADD CONSTRAINT issue_events_event_id_key UNIQUE (event_id);


--
-- Name: issue_events issue_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_events
    ADD CONSTRAINT issue_events_pkey PRIMARY KEY (id);


--
-- Name: issue_hierarchy issue_hierarchy_child_issue_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy
    ADD CONSTRAINT issue_hierarchy_child_issue_id_key UNIQUE (child_issue_id);


--
-- Name: issue_hierarchy issue_hierarchy_parent_issue_id_child_issue_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy
    ADD CONSTRAINT issue_hierarchy_parent_issue_id_child_issue_id_key UNIQUE (parent_issue_id, child_issue_id);


--
-- Name: issue_hierarchy issue_hierarchy_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy
    ADD CONSTRAINT issue_hierarchy_pkey PRIMARY KEY (id);


--
-- Name: issue_hierarchy_policies issue_hierarchy_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy_policies
    ADD CONSTRAINT issue_hierarchy_policies_pkey PRIMARY KEY (id);


--
-- Name: issue_hierarchy_policies issue_hierarchy_policies_project_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy_policies
    ADD CONSTRAINT issue_hierarchy_policies_project_id_key UNIQUE (project_id);


--
-- Name: issue_key_aliases issue_key_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_key_aliases
    ADD CONSTRAINT issue_key_aliases_pkey PRIMARY KEY (id);


--
-- Name: issue_key_aliases issue_key_aliases_tenant_id_issue_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_key_aliases
    ADD CONSTRAINT issue_key_aliases_tenant_id_issue_key_key UNIQUE (tenant_id, issue_key);


--
-- Name: issue_property_values issue_property_values_issue_id_property_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_property_values
    ADD CONSTRAINT issue_property_values_issue_id_property_id_key UNIQUE (issue_id, property_id);


--
-- Name: issue_property_values issue_property_values_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_property_values
    ADD CONSTRAINT issue_property_values_pkey PRIMARY KEY (id);


--
-- Name: issue_sprint_history issue_sprint_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_sprint_history
    ADD CONSTRAINT issue_sprint_history_pkey PRIMARY KEY (id);


--
-- Name: issue_status_history issue_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_status_history
    ADD CONSTRAINT issue_status_history_pkey PRIMARY KEY (id);


--
-- Name: issue_statuses issue_statuses_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_statuses
    ADD CONSTRAINT issue_statuses_api_id_key UNIQUE (api_id);


--
-- Name: issue_statuses issue_statuses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_statuses
    ADD CONSTRAINT issue_statuses_pkey PRIMARY KEY (id);


--
-- Name: issue_statuses issue_statuses_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_statuses
    ADD CONSTRAINT issue_statuses_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: issue_subtype_constraints issue_subtype_constraints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_subtype_constraints
    ADD CONSTRAINT issue_subtype_constraints_pkey PRIMARY KEY (id);


--
-- Name: issue_subtype_constraints issue_subtype_constraints_tenant_id_project_id_parent_issue_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_subtype_constraints
    ADD CONSTRAINT issue_subtype_constraints_tenant_id_project_id_parent_issue_key UNIQUE (tenant_id, project_id, parent_issue_type_id, child_issue_type_id);


--
-- Name: issue_type_config_access_rules issue_type_config_access_rules_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_access_rules
    ADD CONSTRAINT issue_type_config_access_rules_api_id_key UNIQUE (api_id);


--
-- Name: issue_type_config_access_rules issue_type_config_access_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_access_rules
    ADD CONSTRAINT issue_type_config_access_rules_pkey PRIMARY KEY (id);


--
-- Name: issue_type_config_properties issue_type_config_properties_issue_type_config_id_property__key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_properties
    ADD CONSTRAINT issue_type_config_properties_issue_type_config_id_property__key UNIQUE (issue_type_config_id, property_id);


--
-- Name: issue_type_config_properties issue_type_config_properties_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_properties
    ADD CONSTRAINT issue_type_config_properties_pkey PRIMARY KEY (id);


--
-- Name: issue_type_config_statuses issue_type_config_statuses_issue_type_config_id_status_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_statuses
    ADD CONSTRAINT issue_type_config_statuses_issue_type_config_id_status_id_key UNIQUE (issue_type_config_id, status_id);


--
-- Name: issue_type_config_statuses issue_type_config_statuses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_statuses
    ADD CONSTRAINT issue_type_config_statuses_pkey PRIMARY KEY (id);


--
-- Name: issue_type_configs issue_type_configs_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_configs
    ADD CONSTRAINT issue_type_configs_api_id_key UNIQUE (api_id);


--
-- Name: issue_type_configs issue_type_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_configs
    ADD CONSTRAINT issue_type_configs_pkey PRIMARY KEY (id);


--
-- Name: issue_type_configs issue_type_configs_tenant_id_scope_project_id_issue_type_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_configs
    ADD CONSTRAINT issue_type_configs_tenant_id_scope_project_id_issue_type_id_key UNIQUE (tenant_id, scope, project_id, issue_type_id, version);


--
-- Name: issue_types issue_types_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_types
    ADD CONSTRAINT issue_types_api_id_key UNIQUE (api_id);


--
-- Name: issue_types issue_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_types
    ADD CONSTRAINT issue_types_pkey PRIMARY KEY (id);


--
-- Name: issue_types issue_types_tenant_id_project_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_types
    ADD CONSTRAINT issue_types_tenant_id_project_id_code_key UNIQUE (tenant_id, project_id, code);


--
-- Name: issues issues_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_api_id_key UNIQUE (api_id);


--
-- Name: issues issues_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_pkey PRIMARY KEY (id);


--
-- Name: issues issues_project_id_sequence_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_project_id_sequence_no_key UNIQUE (project_id, sequence_no);


--
-- Name: login_account_parameters login_account_parameters_login_account_id_parameter_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_account_parameters
    ADD CONSTRAINT login_account_parameters_login_account_id_parameter_key_key UNIQUE (login_account_id, parameter_key);


--
-- Name: login_account_parameters login_account_parameters_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_account_parameters
    ADD CONSTRAINT login_account_parameters_pkey PRIMARY KEY (id);


--
-- Name: login_accounts login_accounts_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_accounts
    ADD CONSTRAINT login_accounts_api_id_key UNIQUE (api_id);


--
-- Name: login_accounts login_accounts_login_method_id_normalized_subject_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_accounts
    ADD CONSTRAINT login_accounts_login_method_id_normalized_subject_key UNIQUE (login_method_id, normalized_subject);


--
-- Name: login_accounts login_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_accounts
    ADD CONSTRAINT login_accounts_pkey PRIMARY KEY (id);


--
-- Name: login_method_definitions login_method_definitions_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_method_definitions
    ADD CONSTRAINT login_method_definitions_api_id_key UNIQUE (api_id);


--
-- Name: login_method_definitions login_method_definitions_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_method_definitions
    ADD CONSTRAINT login_method_definitions_code_key UNIQUE (code);


--
-- Name: login_method_definitions login_method_definitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_method_definitions
    ADD CONSTRAINT login_method_definitions_pkey PRIMARY KEY (id);


--
-- Name: magic_link_tokens magic_link_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.magic_link_tokens
    ADD CONSTRAINT magic_link_tokens_pkey PRIMARY KEY (id);


--
-- Name: magic_link_tokens magic_link_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.magic_link_tokens
    ADD CONSTRAINT magic_link_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: notification_deliveries notification_deliveries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_deliveries
    ADD CONSTRAINT notification_deliveries_pkey PRIMARY KEY (id);


--
-- Name: notification_preferences notification_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT notification_preferences_pkey PRIMARY KEY (user_id, notification_type);


--
-- Name: notifications notifications_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_api_id_key UNIQUE (api_id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: permission_actions permission_actions_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_actions
    ADD CONSTRAINT permission_actions_code_key UNIQUE (code);


--
-- Name: permission_actions permission_actions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_actions
    ADD CONSTRAINT permission_actions_pkey PRIMARY KEY (id);


--
-- Name: permission_bindings permission_bindings_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_bindings
    ADD CONSTRAINT permission_bindings_api_id_key UNIQUE (api_id);


--
-- Name: permission_bindings permission_bindings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_bindings
    ADD CONSTRAINT permission_bindings_pkey PRIMARY KEY (id);


--
-- Name: permission_policies permission_policies_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_policies
    ADD CONSTRAINT permission_policies_api_id_key UNIQUE (api_id);


--
-- Name: permission_policies permission_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_policies
    ADD CONSTRAINT permission_policies_pkey PRIMARY KEY (id);


--
-- Name: permission_policies permission_policies_tenant_code_uniq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_policies
    ADD CONSTRAINT permission_policies_tenant_code_uniq UNIQUE (tenant_id, code);


--
-- Name: permission_policy_rules permission_policy_rules_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_policy_rules
    ADD CONSTRAINT permission_policy_rules_api_id_key UNIQUE (api_id);


--
-- Name: permission_policy_rules permission_policy_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_policy_rules
    ADD CONSTRAINT permission_policy_rules_pkey PRIMARY KEY (id);


--
-- Name: priorities priorities_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.priorities
    ADD CONSTRAINT priorities_api_id_key UNIQUE (api_id);


--
-- Name: priorities priorities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.priorities
    ADD CONSTRAINT priorities_pkey PRIMARY KEY (id);


--
-- Name: priorities priorities_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.priorities
    ADD CONSTRAINT priorities_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: processed_domain_events processed_domain_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.processed_domain_events
    ADD CONSTRAINT processed_domain_events_pkey PRIMARY KEY (consumer_name, event_id);


--
-- Name: project_identifier_aliases project_identifier_aliases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_identifier_aliases
    ADD CONSTRAINT project_identifier_aliases_pkey PRIMARY KEY (id);


--
-- Name: project_identifier_aliases project_identifier_aliases_tenant_id_identifier_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_identifier_aliases
    ADD CONSTRAINT project_identifier_aliases_tenant_id_identifier_key UNIQUE (tenant_id, identifier);


--
-- Name: projects projects_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_api_id_key UNIQUE (api_id);


--
-- Name: projects projects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: projects projects_tenant_id_identifier_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_tenant_id_identifier_key UNIQUE (tenant_id, identifier);


--
-- Name: property_definitions property_definitions_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_definitions
    ADD CONSTRAINT property_definitions_api_id_key UNIQUE (api_id);


--
-- Name: property_definitions property_definitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_definitions
    ADD CONSTRAINT property_definitions_pkey PRIMARY KEY (id);


--
-- Name: property_definitions property_definitions_tenant_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_definitions
    ADD CONSTRAINT property_definitions_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: property_options property_options_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_options
    ADD CONSTRAINT property_options_api_id_key UNIQUE (api_id);


--
-- Name: property_options property_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_options
    ADD CONSTRAINT property_options_pkey PRIMARY KEY (id);


--
-- Name: property_options property_options_property_id_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_options
    ADD CONSTRAINT property_options_property_id_code_key UNIQUE (property_id, code);


--
-- Name: work_item_views saved_filters_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_views
    ADD CONSTRAINT saved_filters_api_id_key UNIQUE (api_id);


--
-- Name: work_item_views saved_filters_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_views
    ADD CONSTRAINT saved_filters_pkey PRIMARY KEY (id);


--
-- Name: sprint_close_operations sprint_close_operations_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprint_close_operations
    ADD CONSTRAINT sprint_close_operations_api_id_key UNIQUE (api_id);


--
-- Name: sprint_close_operations sprint_close_operations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprint_close_operations
    ADD CONSTRAINT sprint_close_operations_pkey PRIMARY KEY (id);


--
-- Name: sprints sprints_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprints
    ADD CONSTRAINT sprints_api_id_key UNIQUE (api_id);


--
-- Name: sprints sprints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprints
    ADD CONSTRAINT sprints_pkey PRIMARY KEY (id);


--
-- Name: tenant_config_entries tenant_config_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_config_entries
    ADD CONSTRAINT tenant_config_entries_pkey PRIMARY KEY (id);


--
-- Name: tenant_config_entries tenant_config_entries_tenant_id_config_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_config_entries
    ADD CONSTRAINT tenant_config_entries_tenant_id_config_key_key UNIQUE (tenant_id, config_key);


--
-- Name: tenant_login_method_settings tenant_login_method_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_login_method_settings
    ADD CONSTRAINT tenant_login_method_settings_pkey PRIMARY KEY (id);


--
-- Name: tenant_login_method_settings tenant_login_method_settings_tenant_id_login_method_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_login_method_settings
    ADD CONSTRAINT tenant_login_method_settings_tenant_id_login_method_id_key UNIQUE (tenant_id, login_method_id);


--
-- Name: tenant_members tenant_members_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_members
    ADD CONSTRAINT tenant_members_api_id_key UNIQUE (api_id);


--
-- Name: tenant_members tenant_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_members
    ADD CONSTRAINT tenant_members_pkey PRIMARY KEY (id);


--
-- Name: tenant_members tenant_members_tenant_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_members
    ADD CONSTRAINT tenant_members_tenant_id_user_id_key UNIQUE (tenant_id, user_id);


--
-- Name: tenants tenants_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_api_id_key UNIQUE (api_id);


--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- Name: tenants tenants_slug_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_slug_key UNIQUE (slug);


--
-- Name: notifications uq_notification_dedupe; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT uq_notification_dedupe UNIQUE (source_event_id, recipient_user_id, notification_type);


--
-- Name: notification_deliveries uq_notification_delivery_channel; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_deliveries
    ADD CONSTRAINT uq_notification_delivery_channel UNIQUE (notification_id, channel);


--
-- Name: work_item_events uq_work_item_events_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_events
    ADD CONSTRAINT uq_work_item_events_sequence UNIQUE (work_item_id, sequence);


--
-- Name: work_item_timeline_entries uq_work_item_timeline_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_timeline_entries
    ADD CONSTRAINT uq_work_item_timeline_event UNIQUE (work_item_id, event_id);


--
-- Name: work_item_timeline_entries uq_work_item_timeline_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_timeline_entries
    ADD CONSTRAINT uq_work_item_timeline_sequence UNIQUE (work_item_id, sequence);


--
-- Name: user_login_accounts user_login_accounts_login_account_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_login_accounts
    ADD CONSTRAINT user_login_accounts_login_account_id_key UNIQUE (login_account_id);


--
-- Name: user_login_accounts user_login_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_login_accounts
    ADD CONSTRAINT user_login_accounts_pkey PRIMARY KEY (id);


--
-- Name: user_login_accounts user_login_accounts_user_id_login_account_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_login_accounts
    ADD CONSTRAINT user_login_accounts_user_id_login_account_id_key UNIQUE (user_id, login_account_id);


--
-- Name: users users_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_api_id_key UNIQUE (api_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: work_item_events work_item_events_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_events
    ADD CONSTRAINT work_item_events_api_id_key UNIQUE (api_id);


--
-- Name: work_item_events work_item_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_events
    ADD CONSTRAINT work_item_events_pkey PRIMARY KEY (id);


--
-- Name: work_item_timeline_entries work_item_timeline_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_timeline_entries
    ADD CONSTRAINT work_item_timeline_entries_pkey PRIMARY KEY (id);


--
-- Name: workflow_transitions workflow_transitions_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_transitions
    ADD CONSTRAINT workflow_transitions_api_id_key UNIQUE (api_id);


--
-- Name: workflow_transitions workflow_transitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_transitions
    ADD CONSTRAINT workflow_transitions_pkey PRIMARY KEY (id);


--
-- Name: workflows workflows_api_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflows
    ADD CONSTRAINT workflows_api_id_key UNIQUE (api_id);


--
-- Name: workflows workflows_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflows
    ADD CONSTRAINT workflows_pkey PRIMARY KEY (id);


--
-- Name: workflows workflows_tenant_id_code_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflows
    ADD CONSTRAINT workflows_tenant_id_code_version_key UNIQUE (tenant_id, code, version);


--
-- Name: admin_users_instance_active_uniq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX admin_users_instance_active_uniq ON public.admin_users USING btree (user_id) WHERE ((scope = 'instance'::text) AND (tenant_id IS NULL) AND (status = 'active'::text) AND (valid_to IS NULL));


--
-- Name: admin_users_tenant_active_uniq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX admin_users_tenant_active_uniq ON public.admin_users USING btree (user_id, tenant_id) WHERE ((scope = 'tenant'::text) AND (tenant_id IS NOT NULL) AND (status = 'active'::text) AND (valid_to IS NULL));


--
-- Name: group_members_active_uniq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX group_members_active_uniq ON public.group_members USING btree (group_id, user_id) WHERE (status = 'active'::text);


--
-- Name: idx_access_grants_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_access_grants_scope ON public.access_grants USING btree (scope);


--
-- Name: idx_access_grants_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_access_grants_subject ON public.access_grants USING btree (subject_user_id);


--
-- Name: idx_access_grants_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_access_grants_tenant ON public.access_grants USING btree (tenant_id) WHERE (tenant_id IS NOT NULL);


--
-- Name: idx_admin_users_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_users_tenant_id ON public.admin_users USING btree (tenant_id) WHERE (tenant_id IS NOT NULL);


--
-- Name: idx_admin_users_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_users_user_id ON public.admin_users USING btree (user_id);


--
-- Name: idx_attachments_comment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachments_comment ON public.attachments USING btree (tenant_id, comment_id);


--
-- Name: idx_attachments_issue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachments_issue ON public.attachments USING btree (tenant_id, issue_id);


--
-- Name: idx_attachments_issue_purpose; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachments_issue_purpose ON public.attachments USING btree (tenant_id, issue_id, purpose, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_attachments_pending_cleanup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_attachments_pending_cleanup ON public.attachments USING btree (created_at) WHERE ((upload_status = 'pending'::text) AND (deleted_at IS NULL));


--
-- Name: idx_audit_logs_action_occurred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_action_occurred ON public.audit_logs USING btree (tenant_id, action, occurred_at);


--
-- Name: idx_audit_logs_actor_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_actor_user_id ON public.audit_logs USING btree (actor_user_id);


--
-- Name: idx_audit_logs_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_resource ON public.audit_logs USING btree (tenant_id, resource_type, resource_id);


--
-- Name: idx_auth_events_login_account_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_events_login_account_id ON public.auth_events USING btree (login_account_id);


--
-- Name: idx_auth_events_type_occurred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_events_type_occurred ON public.auth_events USING btree (tenant_id, event_type, occurred_at);


--
-- Name: idx_auth_events_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_events_user_id ON public.auth_events USING btree (user_id);


--
-- Name: idx_auth_login_states_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_login_states_expires ON public.auth_login_states USING btree (expires_at) WHERE (consumed_at IS NULL);


--
-- Name: idx_auth_sessions_active_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_sessions_active_tenant ON public.auth_sessions USING btree (active_tenant_id) WHERE (active_tenant_id IS NOT NULL);


--
-- Name: idx_auth_sessions_user_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_sessions_user_active ON public.auth_sessions USING btree (user_id, expires_at, revoked_at);


--
-- Name: idx_bearer_tokens_tenant_user_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bearer_tokens_tenant_user_active ON public.bearer_tokens USING btree (tenant_id, user_id, expires_at, revoked_at);


--
-- Name: idx_bearer_tokens_user_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bearer_tokens_user_active ON public.bearer_tokens USING btree (user_id, expires_at, revoked_at);


--
-- Name: idx_domain_outbox_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_domain_outbox_pending ON public.domain_outbox USING btree (next_attempt_at, created_at) WHERE (status = ANY (ARRAY['PENDING'::text, 'RETRY'::text]));


--
-- Name: idx_group_members_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_group_members_user_id ON public.group_members USING btree (user_id);


--
-- Name: idx_groups_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_groups_tenant_id ON public.groups USING btree (tenant_id);


--
-- Name: idx_invitations_tenant_type_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_invitations_tenant_type_active ON public.invitations USING btree (tenant_id, invitation_type) WHERE (consumed_at IS NULL);


--
-- Name: idx_issue_comments_issue_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_comments_issue_created ON public.issue_comments USING btree (tenant_id, issue_id, created_at);


--
-- Name: idx_issue_comments_transition; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_comments_transition ON public.issue_comments USING btree (tenant_id, transition_id) WHERE (transition_id IS NOT NULL);


--
-- Name: idx_issue_events_issue_occurred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_events_issue_occurred ON public.issue_events USING btree (tenant_id, issue_id, occurred_at);


--
-- Name: idx_issue_events_type_occurred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_events_type_occurred ON public.issue_events USING btree (tenant_id, event_type, occurred_at);


--
-- Name: idx_issue_hierarchy_child; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_hierarchy_child ON public.issue_hierarchy USING btree (tenant_id, child_issue_id);


--
-- Name: idx_issue_hierarchy_parent_rank; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_hierarchy_parent_rank ON public.issue_hierarchy USING btree (tenant_id, parent_issue_id, rank);


--
-- Name: idx_issue_hierarchy_policies_tenant_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_hierarchy_policies_tenant_project ON public.issue_hierarchy_policies USING btree (tenant_id, project_id);


--
-- Name: idx_issue_hierarchy_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_hierarchy_project ON public.issue_hierarchy USING btree (tenant_id, project_id);


--
-- Name: idx_issue_key_aliases_issue_current; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_key_aliases_issue_current ON public.issue_key_aliases USING btree (issue_id, is_current);


--
-- Name: idx_issue_key_aliases_project_sequence; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_key_aliases_project_sequence ON public.issue_key_aliases USING btree (project_id, sequence_no);


--
-- Name: idx_issue_property_values_option; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_property_values_option ON public.issue_property_values USING btree (value_option_id);


--
-- Name: idx_issue_property_values_tenant_property; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_property_values_tenant_property ON public.issue_property_values USING btree (tenant_id, property_id);


--
-- Name: idx_issue_property_values_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_property_values_user ON public.issue_property_values USING btree (value_user_id);


--
-- Name: idx_issue_sprint_history_issue_sprint; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_sprint_history_issue_sprint ON public.issue_sprint_history USING btree (tenant_id, issue_id, sprint_id);


--
-- Name: idx_issue_sprint_history_sprint; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_sprint_history_sprint ON public.issue_sprint_history USING btree (tenant_id, sprint_id);


--
-- Name: idx_issue_status_history_issue_changed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_status_history_issue_changed ON public.issue_status_history USING btree (tenant_id, issue_id, changed_at);


--
-- Name: idx_issue_status_history_status_changed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_status_history_status_changed ON public.issue_status_history USING btree (tenant_id, to_status_id, changed_at);


--
-- Name: idx_issue_statuses_tenant_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_statuses_tenant_group ON public.issue_statuses USING btree (tenant_id, status_group);


--
-- Name: idx_issue_subtype_constraints_child; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_subtype_constraints_child ON public.issue_subtype_constraints USING btree (tenant_id, child_issue_type_id);


--
-- Name: idx_issue_subtype_constraints_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_subtype_constraints_parent ON public.issue_subtype_constraints USING btree (tenant_id, project_id, parent_issue_type_id);


--
-- Name: idx_issue_type_config_access_rules_config; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_type_config_access_rules_config ON public.issue_type_config_access_rules USING btree (issue_type_config_id) WHERE (is_active = true);


--
-- Name: idx_issue_type_config_access_rules_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_type_config_access_rules_tenant ON public.issue_type_config_access_rules USING btree (tenant_id);


--
-- Name: idx_issue_type_config_properties_tenant_config_rank; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_type_config_properties_tenant_config_rank ON public.issue_type_config_properties USING btree (tenant_id, issue_type_config_id, rank);


--
-- Name: idx_issue_type_config_statuses_tenant_config; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_type_config_statuses_tenant_config ON public.issue_type_config_statuses USING btree (tenant_id, issue_type_config_id);


--
-- Name: idx_issue_type_configs_type_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_type_configs_type_scope ON public.issue_type_configs USING btree (tenant_id, issue_type_id, scope, project_id, valid_to);


--
-- Name: idx_issue_type_configs_workflow; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_type_configs_workflow ON public.issue_type_configs USING btree (tenant_id, workflow_id);


--
-- Name: idx_issue_types_tenant_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issue_types_tenant_scope ON public.issue_types USING btree (tenant_id, scope);


--
-- Name: idx_issues_tenant_assignee; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issues_tenant_assignee ON public.issues USING btree (tenant_id, assignee_id);


--
-- Name: idx_issues_tenant_deleted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issues_tenant_deleted_at ON public.issues USING btree (tenant_id, deleted_at);


--
-- Name: idx_issues_tenant_project_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issues_tenant_project_status ON public.issues USING btree (tenant_id, project_id, status_id);


--
-- Name: idx_issues_tenant_reporter; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issues_tenant_reporter ON public.issues USING btree (tenant_id, reporter_id);


--
-- Name: idx_issues_tenant_sprint; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issues_tenant_sprint ON public.issues USING btree (tenant_id, sprint_id);


--
-- Name: idx_issues_tenant_type_config; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_issues_tenant_type_config ON public.issues USING btree (tenant_id, issue_type_config_id);


--
-- Name: idx_magic_link_tokens_subject_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_magic_link_tokens_subject_active ON public.magic_link_tokens USING btree (normalized_subject, expires_at) WHERE (consumed_at IS NULL);


--
-- Name: idx_notifications_recipient_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_recipient_created ON public.notifications USING btree (recipient_user_id, tenant_id, created_at DESC);


--
-- Name: idx_permission_bindings_principal_group_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_bindings_principal_group_id ON public.permission_bindings USING btree (principal_group_id) WHERE (principal_group_id IS NOT NULL);


--
-- Name: idx_permission_bindings_principal_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_bindings_principal_user_id ON public.permission_bindings USING btree (principal_user_id) WHERE (principal_user_id IS NOT NULL);


--
-- Name: idx_permission_bindings_project_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_bindings_project_id ON public.permission_bindings USING btree (project_id) WHERE (project_id IS NOT NULL);


--
-- Name: idx_permission_bindings_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_bindings_tenant_id ON public.permission_bindings USING btree (tenant_id);


--
-- Name: idx_permission_policies_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_policies_tenant_id ON public.permission_policies USING btree (tenant_id);


--
-- Name: idx_permission_policy_rules_policy_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permission_policy_rules_policy_id ON public.permission_policy_rules USING btree (policy_id);


--
-- Name: idx_priorities_tenant_rank; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_priorities_tenant_rank ON public.priorities USING btree (tenant_id, rank);


--
-- Name: idx_project_identifier_aliases_project_current; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_identifier_aliases_project_current ON public.project_identifier_aliases USING btree (project_id, is_current);


--
-- Name: idx_projects_tenant_deleted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_tenant_deleted_at ON public.projects USING btree (tenant_id, deleted_at);


--
-- Name: idx_projects_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_tenant_status ON public.projects USING btree (tenant_id, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_property_definitions_tenant_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_property_definitions_tenant_type ON public.property_definitions USING btree (tenant_id, data_type);


--
-- Name: idx_property_options_tenant_property_rank; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_property_options_tenant_property_rank ON public.property_options USING btree (tenant_id, property_id, rank);


--
-- Name: idx_sprint_close_operations_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sprint_close_operations_lookup ON public.sprint_close_operations USING btree (tenant_id, project_id, sprint_id, created_at DESC);


--
-- Name: idx_sprints_tenant_project_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sprints_tenant_project_status ON public.sprints USING btree (tenant_id, project_id, status);


--
-- Name: idx_tenant_config_entries_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_config_entries_tenant_id ON public.tenant_config_entries USING btree (tenant_id);


--
-- Name: idx_tenant_login_method_settings_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_login_method_settings_enabled ON public.tenant_login_method_settings USING btree (tenant_id, is_enabled);


--
-- Name: idx_tenant_members_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_members_tenant_status ON public.tenant_members USING btree (tenant_id, status);


--
-- Name: idx_tenant_members_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_members_user_id ON public.tenant_members USING btree (user_id);


--
-- Name: idx_tenants_deleted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenants_deleted_at ON public.tenants USING btree (deleted_at);


--
-- Name: idx_tenants_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenants_status ON public.tenants USING btree (status) WHERE (deleted_at IS NULL);


--
-- Name: idx_users_primary_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_primary_email ON public.users USING btree (primary_email);


--
-- Name: idx_work_item_events_timeline; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_item_events_timeline ON public.work_item_events USING btree (tenant_id, work_item_id, sequence DESC);


--
-- Name: idx_work_item_timeline_cursor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_item_timeline_cursor ON public.work_item_timeline_entries USING btree (tenant_id, work_item_id, sequence DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_work_item_views_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_item_views_owner ON public.work_item_views USING btree (tenant_id, owner_id);


--
-- Name: idx_work_item_views_project_visibility; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_item_views_project_visibility ON public.work_item_views USING btree (tenant_id, project_id, visibility) WHERE (project_id IS NOT NULL);


--
-- Name: idx_work_item_views_tenant_visibility; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_item_views_tenant_visibility ON public.work_item_views USING btree (tenant_id, visibility) WHERE (project_id IS NULL);


--
-- Name: idx_workflow_transitions_status_pair; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_transitions_status_pair ON public.workflow_transitions USING btree (workflow_id, from_status_id, to_status_id);


--
-- Name: idx_workflow_transitions_tenant_workflow; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflow_transitions_tenant_workflow ON public.workflow_transitions USING btree (tenant_id, workflow_id);


--
-- Name: idx_workflows_tenant_code_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workflows_tenant_code_active ON public.workflows USING btree (tenant_id, code, is_active);


--
-- Name: uq_domain_outbox_event_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_domain_outbox_event_id ON public.domain_outbox USING btree (event_id);


--
-- Name: uq_issue_type_config_one_initial_status; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_issue_type_config_one_initial_status ON public.issue_type_config_statuses USING btree (issue_type_config_id) WHERE (is_initial = true);


--
-- Name: uq_issue_type_configs_active_project_override; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_issue_type_configs_active_project_override ON public.issue_type_configs USING btree (tenant_id, project_id, issue_type_id) WHERE ((scope = 'project'::text) AND (project_id IS NOT NULL) AND (is_active = true) AND (valid_to IS NULL));


--
-- Name: uq_issue_type_configs_active_tenant_default; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_issue_type_configs_active_tenant_default ON public.issue_type_configs USING btree (tenant_id, issue_type_id) WHERE ((scope = 'tenant'::text) AND (project_id IS NULL) AND (is_active = true) AND (valid_to IS NULL));


--
-- Name: uq_issue_types_project_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_issue_types_project_code ON public.issue_types USING btree (tenant_id, project_id, code) WHERE ((scope = 'project'::text) AND (project_id IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: uq_issue_types_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_issue_types_tenant_code ON public.issue_types USING btree (tenant_id, code) WHERE ((scope = 'tenant'::text) AND (project_id IS NULL) AND (deleted_at IS NULL));


--
-- Name: uq_sprint_close_operation_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_sprint_close_operation_idempotency ON public.sprint_close_operations USING btree (tenant_id, project_id, sprint_id, idempotency_key) WHERE (idempotency_key IS NOT NULL);


--
-- Name: access_grants access_grants_action_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.access_grants
    ADD CONSTRAINT access_grants_action_fkey FOREIGN KEY (action) REFERENCES public.permission_actions(code);


--
-- Name: access_grants access_grants_granted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.access_grants
    ADD CONSTRAINT access_grants_granted_by_fkey FOREIGN KEY (granted_by) REFERENCES public.users(id);


--
-- Name: access_grants access_grants_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.access_grants
    ADD CONSTRAINT access_grants_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: access_grants access_grants_subject_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.access_grants
    ADD CONSTRAINT access_grants_subject_user_id_fkey FOREIGN KEY (subject_user_id) REFERENCES public.users(id);


--
-- Name: access_grants access_grants_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.access_grants
    ADD CONSTRAINT access_grants_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: admin_users admin_users_granted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT admin_users_granted_by_fkey FOREIGN KEY (granted_by) REFERENCES public.users(id);


--
-- Name: admin_users admin_users_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT admin_users_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: admin_users admin_users_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT admin_users_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: attachments attachments_archived_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments
    ADD CONSTRAINT attachments_archived_by_fkey FOREIGN KEY (archived_by) REFERENCES public.users(id);


--
-- Name: attachments attachments_comment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments
    ADD CONSTRAINT attachments_comment_id_fkey FOREIGN KEY (comment_id) REFERENCES public.issue_comments(id);


--
-- Name: attachments attachments_deleted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments
    ADD CONSTRAINT attachments_deleted_by_fkey FOREIGN KEY (deleted_by) REFERENCES public.users(id);


--
-- Name: attachments attachments_issue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments
    ADD CONSTRAINT attachments_issue_id_fkey FOREIGN KEY (issue_id) REFERENCES public.issues(id);


--
-- Name: attachments attachments_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments
    ADD CONSTRAINT attachments_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: attachments attachments_uploaded_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attachments
    ADD CONSTRAINT attachments_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES public.users(id);


--
-- Name: auth_login_states auth_login_states_login_method_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_login_states
    ADD CONSTRAINT auth_login_states_login_method_id_fkey FOREIGN KEY (login_method_id) REFERENCES public.login_method_definitions(id);


--
-- Name: auth_login_states auth_login_states_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_login_states
    ADD CONSTRAINT auth_login_states_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: auth_sessions auth_sessions_active_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT auth_sessions_active_tenant_id_fkey FOREIGN KEY (active_tenant_id) REFERENCES public.tenants(id);


--
-- Name: auth_sessions auth_sessions_login_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT auth_sessions_login_account_id_fkey FOREIGN KEY (login_account_id) REFERENCES public.login_accounts(id);


--
-- Name: auth_sessions auth_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_sessions
    ADD CONSTRAINT auth_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: bearer_tokens bearer_tokens_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bearer_tokens
    ADD CONSTRAINT bearer_tokens_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: bearer_tokens bearer_tokens_login_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bearer_tokens
    ADD CONSTRAINT bearer_tokens_login_account_id_fkey FOREIGN KEY (login_account_id) REFERENCES public.login_accounts(id);


--
-- Name: bearer_tokens bearer_tokens_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bearer_tokens
    ADD CONSTRAINT bearer_tokens_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: bearer_tokens bearer_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bearer_tokens
    ADD CONSTRAINT bearer_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: group_members group_members_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.group_members
    ADD CONSTRAINT group_members_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.groups(id);


--
-- Name: group_members group_members_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.group_members
    ADD CONSTRAINT group_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: groups groups_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT groups_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: invitations invitations_invited_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invitations
    ADD CONSTRAINT invitations_invited_by_fkey FOREIGN KEY (invited_by) REFERENCES public.users(id);


--
-- Name: invitations invitations_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invitations
    ADD CONSTRAINT invitations_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_comments issue_comments_archived_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_comments
    ADD CONSTRAINT issue_comments_archived_by_fkey FOREIGN KEY (archived_by) REFERENCES public.users(id);


--
-- Name: issue_comments issue_comments_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_comments
    ADD CONSTRAINT issue_comments_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.users(id);


--
-- Name: issue_comments issue_comments_deleted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_comments
    ADD CONSTRAINT issue_comments_deleted_by_fkey FOREIGN KEY (deleted_by) REFERENCES public.users(id);


--
-- Name: issue_comments issue_comments_issue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_comments
    ADD CONSTRAINT issue_comments_issue_id_fkey FOREIGN KEY (issue_id) REFERENCES public.issues(id);


--
-- Name: issue_comments issue_comments_status_history_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_comments
    ADD CONSTRAINT issue_comments_status_history_id_fkey FOREIGN KEY (status_history_id) REFERENCES public.issue_status_history(id);


--
-- Name: issue_comments issue_comments_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_comments
    ADD CONSTRAINT issue_comments_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_comments issue_comments_transition_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_comments
    ADD CONSTRAINT issue_comments_transition_id_fkey FOREIGN KEY (transition_id) REFERENCES public.workflow_transitions(id);


--
-- Name: issue_hierarchy issue_hierarchy_child_issue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy
    ADD CONSTRAINT issue_hierarchy_child_issue_id_fkey FOREIGN KEY (child_issue_id) REFERENCES public.issues(id);


--
-- Name: issue_hierarchy issue_hierarchy_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy
    ADD CONSTRAINT issue_hierarchy_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: issue_hierarchy issue_hierarchy_parent_issue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy
    ADD CONSTRAINT issue_hierarchy_parent_issue_id_fkey FOREIGN KEY (parent_issue_id) REFERENCES public.issues(id);


--
-- Name: issue_hierarchy_policies issue_hierarchy_policies_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy_policies
    ADD CONSTRAINT issue_hierarchy_policies_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: issue_hierarchy_policies issue_hierarchy_policies_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy_policies
    ADD CONSTRAINT issue_hierarchy_policies_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: issue_hierarchy_policies issue_hierarchy_policies_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy_policies
    ADD CONSTRAINT issue_hierarchy_policies_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_hierarchy issue_hierarchy_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy
    ADD CONSTRAINT issue_hierarchy_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: issue_hierarchy issue_hierarchy_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_hierarchy
    ADD CONSTRAINT issue_hierarchy_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_key_aliases issue_key_aliases_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_key_aliases
    ADD CONSTRAINT issue_key_aliases_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: issue_key_aliases issue_key_aliases_issue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_key_aliases
    ADD CONSTRAINT issue_key_aliases_issue_id_fkey FOREIGN KEY (issue_id) REFERENCES public.issues(id);


--
-- Name: issue_key_aliases issue_key_aliases_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_key_aliases
    ADD CONSTRAINT issue_key_aliases_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: issue_key_aliases issue_key_aliases_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_key_aliases
    ADD CONSTRAINT issue_key_aliases_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_property_values issue_property_values_issue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_property_values
    ADD CONSTRAINT issue_property_values_issue_id_fkey FOREIGN KEY (issue_id) REFERENCES public.issues(id);


--
-- Name: issue_property_values issue_property_values_property_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_property_values
    ADD CONSTRAINT issue_property_values_property_id_fkey FOREIGN KEY (property_id) REFERENCES public.property_definitions(id);


--
-- Name: issue_property_values issue_property_values_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_property_values
    ADD CONSTRAINT issue_property_values_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_property_values issue_property_values_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_property_values
    ADD CONSTRAINT issue_property_values_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id);


--
-- Name: issue_property_values issue_property_values_value_issue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_property_values
    ADD CONSTRAINT issue_property_values_value_issue_id_fkey FOREIGN KEY (value_issue_id) REFERENCES public.issues(id);


--
-- Name: issue_property_values issue_property_values_value_option_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_property_values
    ADD CONSTRAINT issue_property_values_value_option_id_fkey FOREIGN KEY (value_option_id) REFERENCES public.property_options(id);


--
-- Name: issue_property_values issue_property_values_value_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_property_values
    ADD CONSTRAINT issue_property_values_value_project_id_fkey FOREIGN KEY (value_project_id) REFERENCES public.projects(id);


--
-- Name: issue_property_values issue_property_values_value_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_property_values
    ADD CONSTRAINT issue_property_values_value_user_id_fkey FOREIGN KEY (value_user_id) REFERENCES public.users(id);


--
-- Name: issue_sprint_history issue_sprint_history_added_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_sprint_history
    ADD CONSTRAINT issue_sprint_history_added_by_fkey FOREIGN KEY (added_by) REFERENCES public.users(id);


--
-- Name: issue_sprint_history issue_sprint_history_issue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_sprint_history
    ADD CONSTRAINT issue_sprint_history_issue_id_fkey FOREIGN KEY (issue_id) REFERENCES public.issues(id);


--
-- Name: issue_sprint_history issue_sprint_history_removed_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_sprint_history
    ADD CONSTRAINT issue_sprint_history_removed_by_fkey FOREIGN KEY (removed_by) REFERENCES public.users(id);


--
-- Name: issue_sprint_history issue_sprint_history_sprint_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_sprint_history
    ADD CONSTRAINT issue_sprint_history_sprint_id_fkey FOREIGN KEY (sprint_id) REFERENCES public.sprints(id);


--
-- Name: issue_sprint_history issue_sprint_history_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_sprint_history
    ADD CONSTRAINT issue_sprint_history_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_status_history issue_status_history_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_status_history
    ADD CONSTRAINT issue_status_history_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(id);


--
-- Name: issue_status_history issue_status_history_from_status_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_status_history
    ADD CONSTRAINT issue_status_history_from_status_id_fkey FOREIGN KEY (from_status_id) REFERENCES public.issue_statuses(id);


--
-- Name: issue_status_history issue_status_history_issue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_status_history
    ADD CONSTRAINT issue_status_history_issue_id_fkey FOREIGN KEY (issue_id) REFERENCES public.issues(id);


--
-- Name: issue_status_history issue_status_history_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_status_history
    ADD CONSTRAINT issue_status_history_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_status_history issue_status_history_to_status_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_status_history
    ADD CONSTRAINT issue_status_history_to_status_id_fkey FOREIGN KEY (to_status_id) REFERENCES public.issue_statuses(id);


--
-- Name: issue_status_history issue_status_history_transition_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_status_history
    ADD CONSTRAINT issue_status_history_transition_id_fkey FOREIGN KEY (transition_id) REFERENCES public.workflow_transitions(id);


--
-- Name: issue_statuses issue_statuses_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_statuses
    ADD CONSTRAINT issue_statuses_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_subtype_constraints issue_subtype_constraints_child_issue_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_subtype_constraints
    ADD CONSTRAINT issue_subtype_constraints_child_issue_type_id_fkey FOREIGN KEY (child_issue_type_id) REFERENCES public.issue_types(id);


--
-- Name: issue_subtype_constraints issue_subtype_constraints_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_subtype_constraints
    ADD CONSTRAINT issue_subtype_constraints_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: issue_subtype_constraints issue_subtype_constraints_parent_issue_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_subtype_constraints
    ADD CONSTRAINT issue_subtype_constraints_parent_issue_type_id_fkey FOREIGN KEY (parent_issue_type_id) REFERENCES public.issue_types(id);


--
-- Name: issue_subtype_constraints issue_subtype_constraints_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_subtype_constraints
    ADD CONSTRAINT issue_subtype_constraints_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: issue_subtype_constraints issue_subtype_constraints_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_subtype_constraints
    ADD CONSTRAINT issue_subtype_constraints_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_type_config_access_rules issue_type_config_access_rules_issue_type_config_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_access_rules
    ADD CONSTRAINT issue_type_config_access_rules_issue_type_config_id_fkey FOREIGN KEY (issue_type_config_id) REFERENCES public.issue_type_configs(id);


--
-- Name: issue_type_config_access_rules issue_type_config_access_rules_subject_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_access_rules
    ADD CONSTRAINT issue_type_config_access_rules_subject_group_id_fkey FOREIGN KEY (subject_group_id) REFERENCES public.groups(id);


--
-- Name: issue_type_config_access_rules issue_type_config_access_rules_subject_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_access_rules
    ADD CONSTRAINT issue_type_config_access_rules_subject_user_id_fkey FOREIGN KEY (subject_user_id) REFERENCES public.users(id);


--
-- Name: issue_type_config_access_rules issue_type_config_access_rules_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_access_rules
    ADD CONSTRAINT issue_type_config_access_rules_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_type_config_access_rules issue_type_config_access_rules_transition_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_access_rules
    ADD CONSTRAINT issue_type_config_access_rules_transition_id_fkey FOREIGN KEY (transition_id) REFERENCES public.workflow_transitions(id);


--
-- Name: issue_type_config_properties issue_type_config_properties_issue_type_config_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_properties
    ADD CONSTRAINT issue_type_config_properties_issue_type_config_id_fkey FOREIGN KEY (issue_type_config_id) REFERENCES public.issue_type_configs(id);


--
-- Name: issue_type_config_properties issue_type_config_properties_property_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_properties
    ADD CONSTRAINT issue_type_config_properties_property_id_fkey FOREIGN KEY (property_id) REFERENCES public.property_definitions(id);


--
-- Name: issue_type_config_properties issue_type_config_properties_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_properties
    ADD CONSTRAINT issue_type_config_properties_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_type_config_statuses issue_type_config_statuses_issue_type_config_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_statuses
    ADD CONSTRAINT issue_type_config_statuses_issue_type_config_id_fkey FOREIGN KEY (issue_type_config_id) REFERENCES public.issue_type_configs(id);


--
-- Name: issue_type_config_statuses issue_type_config_statuses_status_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_statuses
    ADD CONSTRAINT issue_type_config_statuses_status_id_fkey FOREIGN KEY (status_id) REFERENCES public.issue_statuses(id);


--
-- Name: issue_type_config_statuses issue_type_config_statuses_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_config_statuses
    ADD CONSTRAINT issue_type_config_statuses_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_type_configs issue_type_configs_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_configs
    ADD CONSTRAINT issue_type_configs_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: issue_type_configs issue_type_configs_issue_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_configs
    ADD CONSTRAINT issue_type_configs_issue_type_id_fkey FOREIGN KEY (issue_type_id) REFERENCES public.issue_types(id);


--
-- Name: issue_type_configs issue_type_configs_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_configs
    ADD CONSTRAINT issue_type_configs_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: issue_type_configs issue_type_configs_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_configs
    ADD CONSTRAINT issue_type_configs_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issue_type_configs issue_type_configs_workflow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_type_configs
    ADD CONSTRAINT issue_type_configs_workflow_id_fkey FOREIGN KEY (workflow_id) REFERENCES public.workflows(id);


--
-- Name: issue_types issue_types_archived_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_types
    ADD CONSTRAINT issue_types_archived_by_fkey FOREIGN KEY (archived_by) REFERENCES public.users(id);


--
-- Name: issue_types issue_types_deleted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_types
    ADD CONSTRAINT issue_types_deleted_by_fkey FOREIGN KEY (deleted_by) REFERENCES public.users(id);


--
-- Name: issue_types issue_types_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_types
    ADD CONSTRAINT issue_types_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: issue_types issue_types_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issue_types
    ADD CONSTRAINT issue_types_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issues issues_archived_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_archived_by_fkey FOREIGN KEY (archived_by) REFERENCES public.users(id);


--
-- Name: issues issues_assignee_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_assignee_id_fkey FOREIGN KEY (assignee_id) REFERENCES public.users(id);


--
-- Name: issues issues_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: issues issues_deleted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_deleted_by_fkey FOREIGN KEY (deleted_by) REFERENCES public.users(id);


--
-- Name: issues issues_issue_type_config_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_issue_type_config_id_fkey FOREIGN KEY (issue_type_config_id) REFERENCES public.issue_type_configs(id);


--
-- Name: issues issues_issue_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_issue_type_id_fkey FOREIGN KEY (issue_type_id) REFERENCES public.issue_types(id);


--
-- Name: issues issues_priority_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_priority_id_fkey FOREIGN KEY (priority_id) REFERENCES public.priorities(id);


--
-- Name: issues issues_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: issues issues_reporter_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.users(id);


--
-- Name: issues issues_sprint_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_sprint_id_fkey FOREIGN KEY (sprint_id) REFERENCES public.sprints(id);


--
-- Name: issues issues_status_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_status_id_fkey FOREIGN KEY (status_id) REFERENCES public.issue_statuses(id);


--
-- Name: issues issues_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: issues issues_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.issues
    ADD CONSTRAINT issues_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id);


--
-- Name: login_account_parameters login_account_parameters_login_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_account_parameters
    ADD CONSTRAINT login_account_parameters_login_account_id_fkey FOREIGN KEY (login_account_id) REFERENCES public.login_accounts(id);


--
-- Name: login_accounts login_accounts_disabled_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_accounts
    ADD CONSTRAINT login_accounts_disabled_by_fkey FOREIGN KEY (disabled_by) REFERENCES public.users(id);


--
-- Name: login_accounts login_accounts_login_method_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_accounts
    ADD CONSTRAINT login_accounts_login_method_id_fkey FOREIGN KEY (login_method_id) REFERENCES public.login_method_definitions(id);


--
-- Name: magic_link_tokens magic_link_tokens_login_method_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.magic_link_tokens
    ADD CONSTRAINT magic_link_tokens_login_method_id_fkey FOREIGN KEY (login_method_id) REFERENCES public.login_method_definitions(id);


--
-- Name: magic_link_tokens magic_link_tokens_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.magic_link_tokens
    ADD CONSTRAINT magic_link_tokens_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: notification_deliveries notification_deliveries_notification_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_deliveries
    ADD CONSTRAINT notification_deliveries_notification_id_fkey FOREIGN KEY (notification_id) REFERENCES public.notifications(id) ON DELETE CASCADE;


--
-- Name: notification_preferences notification_preferences_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT notification_preferences_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: notifications notifications_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: notifications notifications_recipient_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_recipient_user_id_fkey FOREIGN KEY (recipient_user_id) REFERENCES public.users(id);


--
-- Name: notifications notifications_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: notifications notifications_work_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_work_item_id_fkey FOREIGN KEY (work_item_id) REFERENCES public.issues(id);


--
-- Name: permission_bindings permission_bindings_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_bindings
    ADD CONSTRAINT permission_bindings_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: permission_bindings permission_bindings_policy_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_bindings
    ADD CONSTRAINT permission_bindings_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES public.permission_policies(id);


--
-- Name: permission_bindings permission_bindings_principal_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_bindings
    ADD CONSTRAINT permission_bindings_principal_group_id_fkey FOREIGN KEY (principal_group_id) REFERENCES public.groups(id);


--
-- Name: permission_bindings permission_bindings_principal_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_bindings
    ADD CONSTRAINT permission_bindings_principal_user_id_fkey FOREIGN KEY (principal_user_id) REFERENCES public.users(id);


--
-- Name: permission_bindings permission_bindings_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_bindings
    ADD CONSTRAINT permission_bindings_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: permission_bindings permission_bindings_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_bindings
    ADD CONSTRAINT permission_bindings_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: permission_policies permission_policies_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_policies
    ADD CONSTRAINT permission_policies_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: permission_policy_rules permission_policy_rules_action_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_policy_rules
    ADD CONSTRAINT permission_policy_rules_action_fkey FOREIGN KEY (action) REFERENCES public.permission_actions(code);


--
-- Name: permission_policy_rules permission_policy_rules_policy_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission_policy_rules
    ADD CONSTRAINT permission_policy_rules_policy_id_fkey FOREIGN KEY (policy_id) REFERENCES public.permission_policies(id);


--
-- Name: priorities priorities_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.priorities
    ADD CONSTRAINT priorities_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: project_identifier_aliases project_identifier_aliases_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_identifier_aliases
    ADD CONSTRAINT project_identifier_aliases_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: project_identifier_aliases project_identifier_aliases_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_identifier_aliases
    ADD CONSTRAINT project_identifier_aliases_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: project_identifier_aliases project_identifier_aliases_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_identifier_aliases
    ADD CONSTRAINT project_identifier_aliases_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: projects projects_archived_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_archived_by_fkey FOREIGN KEY (archived_by) REFERENCES public.users(id);


--
-- Name: projects projects_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: projects projects_deleted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_deleted_by_fkey FOREIGN KEY (deleted_by) REFERENCES public.users(id);


--
-- Name: projects projects_lead_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_lead_user_id_fkey FOREIGN KEY (lead_user_id) REFERENCES public.users(id);


--
-- Name: projects projects_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: property_definitions property_definitions_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_definitions
    ADD CONSTRAINT property_definitions_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: property_options property_options_property_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_options
    ADD CONSTRAINT property_options_property_id_fkey FOREIGN KEY (property_id) REFERENCES public.property_definitions(id);


--
-- Name: property_options property_options_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_options
    ADD CONSTRAINT property_options_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: work_item_views saved_filters_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_views
    ADD CONSTRAINT saved_filters_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(id);


--
-- Name: work_item_views saved_filters_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_views
    ADD CONSTRAINT saved_filters_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: work_item_views saved_filters_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_views
    ADD CONSTRAINT saved_filters_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: sprint_close_operations sprint_close_operations_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprint_close_operations
    ADD CONSTRAINT sprint_close_operations_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: sprint_close_operations sprint_close_operations_requested_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprint_close_operations
    ADD CONSTRAINT sprint_close_operations_requested_by_fkey FOREIGN KEY (requested_by) REFERENCES public.users(id);


--
-- Name: sprint_close_operations sprint_close_operations_sprint_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprint_close_operations
    ADD CONSTRAINT sprint_close_operations_sprint_id_fkey FOREIGN KEY (sprint_id) REFERENCES public.sprints(id);


--
-- Name: sprint_close_operations sprint_close_operations_target_sprint_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprint_close_operations
    ADD CONSTRAINT sprint_close_operations_target_sprint_id_fkey FOREIGN KEY (target_sprint_id) REFERENCES public.sprints(id);


--
-- Name: sprint_close_operations sprint_close_operations_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprint_close_operations
    ADD CONSTRAINT sprint_close_operations_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: sprints sprints_archived_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprints
    ADD CONSTRAINT sprints_archived_by_fkey FOREIGN KEY (archived_by) REFERENCES public.users(id);


--
-- Name: sprints sprints_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprints
    ADD CONSTRAINT sprints_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: sprints sprints_deleted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprints
    ADD CONSTRAINT sprints_deleted_by_fkey FOREIGN KEY (deleted_by) REFERENCES public.users(id);


--
-- Name: sprints sprints_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprints
    ADD CONSTRAINT sprints_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: sprints sprints_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sprints
    ADD CONSTRAINT sprints_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: tenant_config_entries tenant_config_entries_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_config_entries
    ADD CONSTRAINT tenant_config_entries_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: tenant_config_entries tenant_config_entries_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_config_entries
    ADD CONSTRAINT tenant_config_entries_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: tenant_config_entries tenant_config_entries_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_config_entries
    ADD CONSTRAINT tenant_config_entries_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id);


--
-- Name: tenant_login_method_settings tenant_login_method_settings_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_login_method_settings
    ADD CONSTRAINT tenant_login_method_settings_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: tenant_login_method_settings tenant_login_method_settings_login_method_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_login_method_settings
    ADD CONSTRAINT tenant_login_method_settings_login_method_id_fkey FOREIGN KEY (login_method_id) REFERENCES public.login_method_definitions(id);


--
-- Name: tenant_login_method_settings tenant_login_method_settings_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_login_method_settings
    ADD CONSTRAINT tenant_login_method_settings_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: tenant_login_method_settings tenant_login_method_settings_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_login_method_settings
    ADD CONSTRAINT tenant_login_method_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id);


--
-- Name: tenant_members tenant_members_archived_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_members
    ADD CONSTRAINT tenant_members_archived_by_fkey FOREIGN KEY (archived_by) REFERENCES public.users(id);


--
-- Name: tenant_members tenant_members_deleted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_members
    ADD CONSTRAINT tenant_members_deleted_by_fkey FOREIGN KEY (deleted_by) REFERENCES public.users(id);


--
-- Name: tenant_members tenant_members_invited_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_members
    ADD CONSTRAINT tenant_members_invited_by_fkey FOREIGN KEY (invited_by) REFERENCES public.users(id);


--
-- Name: tenant_members tenant_members_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_members
    ADD CONSTRAINT tenant_members_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: tenant_members tenant_members_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_members
    ADD CONSTRAINT tenant_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: tenants tenants_archived_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_archived_by_fkey FOREIGN KEY (archived_by) REFERENCES public.users(id);


--
-- Name: tenants tenants_deleted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_deleted_by_fkey FOREIGN KEY (deleted_by) REFERENCES public.users(id);


--
-- Name: user_login_accounts user_login_accounts_linked_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_login_accounts
    ADD CONSTRAINT user_login_accounts_linked_by_fkey FOREIGN KEY (linked_by) REFERENCES public.users(id);


--
-- Name: user_login_accounts user_login_accounts_login_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_login_accounts
    ADD CONSTRAINT user_login_accounts_login_account_id_fkey FOREIGN KEY (login_account_id) REFERENCES public.login_accounts(id);


--
-- Name: user_login_accounts user_login_accounts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_login_accounts
    ADD CONSTRAINT user_login_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: users users_archived_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_archived_by_fkey FOREIGN KEY (archived_by) REFERENCES public.users(id);


--
-- Name: users users_deleted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_deleted_by_fkey FOREIGN KEY (deleted_by) REFERENCES public.users(id);


--
-- Name: work_item_events work_item_events_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_events
    ADD CONSTRAINT work_item_events_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(id);


--
-- Name: work_item_events work_item_events_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_events
    ADD CONSTRAINT work_item_events_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: work_item_events work_item_events_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_events
    ADD CONSTRAINT work_item_events_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: work_item_events work_item_events_work_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_events
    ADD CONSTRAINT work_item_events_work_item_id_fkey FOREIGN KEY (work_item_id) REFERENCES public.issues(id);


--
-- Name: work_item_timeline_entries work_item_timeline_entries_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_timeline_entries
    ADD CONSTRAINT work_item_timeline_entries_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.work_item_events(id);


--
-- Name: work_item_timeline_entries work_item_timeline_entries_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_timeline_entries
    ADD CONSTRAINT work_item_timeline_entries_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: work_item_timeline_entries work_item_timeline_entries_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_timeline_entries
    ADD CONSTRAINT work_item_timeline_entries_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: work_item_timeline_entries work_item_timeline_entries_work_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_item_timeline_entries
    ADD CONSTRAINT work_item_timeline_entries_work_item_id_fkey FOREIGN KEY (work_item_id) REFERENCES public.issues(id);


--
-- Name: workflow_transitions workflow_transitions_from_status_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_transitions
    ADD CONSTRAINT workflow_transitions_from_status_id_fkey FOREIGN KEY (from_status_id) REFERENCES public.issue_statuses(id);


--
-- Name: workflow_transitions workflow_transitions_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_transitions
    ADD CONSTRAINT workflow_transitions_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: workflow_transitions workflow_transitions_to_status_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_transitions
    ADD CONSTRAINT workflow_transitions_to_status_id_fkey FOREIGN KEY (to_status_id) REFERENCES public.issue_statuses(id);


--
-- Name: workflow_transitions workflow_transitions_workflow_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_transitions
    ADD CONSTRAINT workflow_transitions_workflow_id_fkey FOREIGN KEY (workflow_id) REFERENCES public.workflows(id);


--
-- Name: workflows workflows_archived_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflows
    ADD CONSTRAINT workflows_archived_by_fkey FOREIGN KEY (archived_by) REFERENCES public.users(id);


--
-- Name: workflows workflows_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflows
    ADD CONSTRAINT workflows_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: workflows workflows_deleted_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflows
    ADD CONSTRAINT workflows_deleted_by_fkey FOREIGN KEY (deleted_by) REFERENCES public.users(id);


--
-- Name: workflows workflows_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflows
    ADD CONSTRAINT workflows_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- PostgreSQL database dump complete
--
--
-- PostgreSQL database dump
--


-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: login_method_definitions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.login_method_definitions (id, code, kind, name, is_builtin, is_enabled_globally, config_schema, created_at, updated_at, api_id) VALUES ('ce606503-ee3f-411e-9285-a070ec29f15c', 'password', 'password', 'Password', true, true, '{"credential": "password"}', '2026-07-10 19:56:46.835409+00', '2026-07-10 19:56:46.835409+00', 'lmg_01JABCDEFGHJKMNPQRSTVWXYZ0');
INSERT INTO public.login_method_definitions (id, code, kind, name, is_builtin, is_enabled_globally, config_schema, created_at, updated_at, api_id) VALUES ('71faece8-64e4-4dba-b638-f61e394d8af1', 'instance_password', 'password', 'Workbench Admin', true, true, '{"credential": "password"}', '2026-07-10 19:56:46.846017+00', '2026-07-10 19:56:46.846017+00', 'lmg_01JABCDEFGHJKMNPQRSTVWXYZ1');


--
-- Data for Name: permission_actions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('86b576d2-86ee-4bdd-80fc-37d8c72bfcf3', 'tenant.access', 'Access a tenant scoped API.', '2026-07-10 19:56:46.821139+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('bbc7366e-e823-4c89-ad6e-5d2e3522384e', 'project.create', 'Create projects in a tenant.', '2026-07-10 19:56:46.821139+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('8e94131f-15fa-4285-b289-41a28513f81d', 'project.read', 'Read project data.', '2026-07-10 19:56:46.821139+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('876035aa-6a06-495d-b23e-ec6abc2e8ecc', 'project.manage', 'Manage project settings.', '2026-07-10 19:56:46.821139+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('af23a81f-0c3e-40cb-9851-aa9ae8b57318', 'permission.role.manage', 'Manage roles.', '2026-07-10 19:56:46.821139+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('ed330667-fd65-4189-99f8-59836f17a418', 'permission.policy.manage', 'Manage permission policies.', '2026-07-10 19:56:46.821139+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('14713789-393d-4910-b768-a815bb04dccb', 'permission.assignment.manage', 'Manage role assignments.', '2026-07-10 19:56:46.821139+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('58121e2e-ed70-40eb-be6d-b40158f6cc32', 'tenant.create', 'Create tenants.', '2026-07-10 19:56:46.840476+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('3904d7c6-7418-4273-96dc-df62a25a5d87', 'tenant.read', 'Read tenant metadata.', '2026-07-10 19:56:46.840476+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('97ebdfd1-5e9f-4fc9-8dae-3ecd16ace566', 'tenant.update', 'Update tenant metadata.', '2026-07-10 19:56:46.840476+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('048e2023-9e19-4c32-bd60-bfd5f602109b', 'tenant.delete', 'Destroy tenants.', '2026-07-10 19:56:46.870363+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('125b25e0-f030-49f3-97ad-bae885a250cd', 'permission.group.manage', 'Manage tenant permission groups.', '2026-07-10 19:56:46.876205+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('8eac3d63-b363-4595-b444-d406902bd2d6', 'tenant.member.manage', 'Manage tenant members.', '2026-07-10 19:56:46.876205+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('5b668add-38ab-4d61-a921-23614790698c', 'project.update', 'Update projects.', '2026-07-10 19:56:46.876205+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('64781238-3078-4ebf-8b7a-5d640b038e7d', 'project.delete', 'Delete projects.', '2026-07-10 19:56:46.876205+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('2f2fca25-b9a0-4b4d-989f-c63a4cedd1a5', 'project.archive', 'Archive or unarchive projects.', '2026-07-10 19:56:46.893065+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('74b72ef1-cab4-4ff6-8960-3b46babf6dd3', 'project.join', 'Join an open project as a member.', '2026-07-10 19:56:46.893065+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('9a11acf4-6f8c-4369-84fe-9138badc038a', 'issue.view', 'View work items.', '2026-07-10 19:56:46.907585+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('e1b78ea9-0522-4d59-b079-3014b5a8b1c2', 'issue.create', 'Create work items.', '2026-07-10 19:56:46.907585+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('e641af7a-38f0-4be2-8cf9-bd282f23a739', 'issue.update', 'Update work items.', '2026-07-10 19:56:46.907585+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('83501664-b574-4367-b905-f4035bed678c', 'issue.delete', 'Delete work items.', '2026-07-10 19:56:46.907585+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('8fc4831d-685a-464d-9e3b-40d61c1e09e7', 'issue.assign', 'Assign work items.', '2026-07-10 19:56:46.907585+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('effd5a3b-ae6d-46a4-a0b3-5d2b54f72ca7', 'issue.transition', 'Transition work items.', '2026-07-10 19:56:46.907585+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('caa93094-a2a4-4958-8d0a-0452636049da', 'issue.comment.create', 'Create work item comments.', '2026-07-10 19:56:46.907585+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('cebe6c71-1a6f-4c3a-97b0-6f41f110c9d1', 'issue.comment.delete', 'Delete work item comments.', '2026-07-10 19:56:46.907585+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('6d6f7767-fd6b-46d1-802a-d8e379631f28', 'workflow.manage', 'Manage workflows.', '2026-07-10 19:56:46.907585+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('51256394-056a-440b-9a1a-fcc25b47382a', 'issue.field.write', 'Write a specific work item field.', '2026-07-10 19:56:46.912205+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('c825503a-4a8a-4744-8e0c-fb74168ce149', 'issue.comment.update', 'Update work item comments.', '2026-07-10 19:56:46.929653+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('30f3c598-b7b9-4443-b827-84d8ac27f30b', 'workitem.config.read', 'Read work item configuration.', '2026-07-10 19:56:46.950813+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('c1e2ecc8-c6d0-45be-8f9a-eecb386446e5', 'workitem.config.manage', 'Manage work item configuration.', '2026-07-10 19:56:46.950813+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('054af42d-39d4-4b6d-b3c3-bcdc62db7100', 'issue.attachment.create', 'Create work item attachments.', '2026-07-10 19:56:46.961404+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('d34e8dbc-0256-4a28-9b96-ab73eb711212', 'issue.attachment.delete', 'Delete work item attachments.', '2026-07-10 19:56:46.961404+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('851b9835-7597-4afd-9baa-f06e68e4595e', 'view.read', 'Read work item views.', '2026-07-10 19:56:46.984247+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('dee29b3c-25a0-41b5-804c-d75591ca1995', 'view.create', 'Create work item views.', '2026-07-10 19:56:46.984247+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('6b3d203c-a967-4f46-9b5f-da647705975b', 'view.manage', 'Manage work item views.', '2026-07-10 19:56:46.984247+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('44701bf7-1f24-47fa-923d-64cc25120a51', 'sprint.read', 'Read sprints.', '2026-07-10 19:56:46.989946+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('584cdf35-2482-4eef-8e00-e707c2f3619e', 'sprint.create', 'Create sprints.', '2026-07-10 19:56:46.989946+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('81bc7b94-5568-4701-aa48-d6b6ee6fcce3', 'sprint.manage', 'Manage sprints.', '2026-07-10 19:56:46.989946+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('44c9a23e-6b9d-4ace-b1f3-7c2c921ec528', 'notification.read', 'Read notifications.', '2026-07-10 19:56:47.066515+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('eab75a6d-eb6c-4133-bbd7-abf674dcc65b', 'notification.manage', 'Manage notification preferences.', '2026-07-10 19:56:47.066515+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('9da1b509-d717-4963-81f0-b7ed433727e6', 'sprint.workitem.disposition', 'Change work item sprint membership while closing a sprint.', '2026-07-10 19:56:47.078813+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('7e9b5956-d5ad-45d0-be0d-db727d47db90', 'outbox.read', 'Read domain outbox messages.', '2026-07-10 19:56:47.087349+00');
INSERT INTO public.permission_actions (id, code, description, created_at) VALUES ('20de6516-0bb0-479a-b1f0-c79a612c0499', 'outbox.manage', 'Manage domain outbox messages including replay.', '2026-07-10 19:56:47.087349+00');


--
-- PostgreSQL database dump complete
--
