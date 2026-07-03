package ink.doa.workbench.core.common.errors

private val WorkbenchErrorCodePattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

enum class WorkbenchErrorCode(
  val code: String,
  val defaultMessage: String,
) {
  RESOURCE_TENANT_NOT_FOUND("resource.tenant.not_found", "Tenant not found."),
  RESOURCE_PROJECT_NOT_FOUND("resource.project.not_found", "Project not found."),
  RESOURCE_USER_NOT_FOUND("resource.user.not_found", "User not found."),
  RESOURCE_ADMIN_USER_NOT_FOUND("resource.admin_user.not_found", "Admin user not found."),
  RESOURCE_LOGIN_METHOD_NOT_FOUND("resource.login_method.not_found", "Login method not found."),
  RESOURCE_PASSWORD_LOGIN_METHOD_NOT_FOUND(
    "resource.login_method.password.not_found",
    "Password login method is not configured.",
  ),
  RESOURCE_INSTANCE_PASSWORD_LOGIN_METHOD_NOT_FOUND(
    "resource.login_method.instance_password.not_found",
    "Instance password login method is not configured.",
  ),
  RESOURCE_BEARER_TOKEN_NOT_FOUND("resource.bearer_token.not_found", "Bearer token not found."),
  RESOURCE_ACCESS_GRANT_NOT_FOUND("resource.access_grant.not_found", "Access grant not found."),
  RESOURCE_PERMISSION_POLICY_NOT_FOUND(
    "resource.permission_policy.not_found",
    "Permission policy not found.",
  ),
  RESOURCE_PERMISSION_GROUP_NOT_FOUND(
    "resource.permission_group.not_found",
    "Permission group not found.",
  ),
  RESOURCE_PERMISSION_BINDING_NOT_FOUND(
    "resource.permission_binding.not_found",
    "Permission binding not found.",
  ),
  RESOURCE_PROJECT_MEMBER_POLICY_NOT_FOUND(
    "resource.project_member_policy.not_found",
    "Built-in project-member policy is not configured.",
  ),
  AUTH_AUTHENTICATION_REQUIRED("auth.authentication_required", "Authentication required."),
  AUTH_AUTHENTICATED_USER_REQUIRED(
    "auth.authenticated_user_required",
    "Authenticated user is required.",
  ),
  AUTH_INVALID_CREDENTIALS("auth.invalid_credentials", "Invalid credentials."),
  AUTH_TOKEN_NOT_FOUND("auth.token.not_found", "Token not found."),
  AUTH_TENANT_MEMBERSHIP_REQUIRED(
    "auth.tenant_membership_required",
    "You are not an active member of this tenant.",
  ),
  AUTH_SELECTED_TENANT_MEMBERSHIP_REQUIRED(
    "auth.selected_tenant_membership_required",
    "You are not an active member of the selected tenant.",
  ),
  AUTH_PERMISSION_DENIED("auth.permission.denied", "Permission denied."),
  AUTH_PERMISSION_LEGACY_DISABLED(
    "auth.permission.legacy_disabled",
    "Legacy @RequirePermission is disabled. Use the unified authorization model.",
  ),
  AUTH_PERMISSION_CONTEXT_REQUIRED(
    "auth.permission.context_required",
    "Request context is required for authorization.",
  ),
  AUTH_PERMISSION_MISSING_INSTANCE_ADMIN(
    "auth.permission.missing_instance_admin",
    "Instance administrator permission is required.",
  ),
  AUTH_PERMISSION_TOKEN_SCOPE_DENIED(
    "auth.permission.token_scope_denied",
    "Token scope does not allow the request.",
  ),
  AUTH_PERMISSION_MISSING_TENANT(
    "auth.permission.missing_tenant",
    "Tenant context is required for authorization.",
  ),
  AUTH_PERMISSION_MISSING_MEMBERSHIP(
    "auth.permission.missing_membership",
    "Tenant membership is required.",
  ),
  AUTH_PERMISSION_INACTIVE_MEMBERSHIP(
    "auth.permission.inactive_membership",
    "Tenant membership is inactive.",
  ),
  AUTH_PERMISSION_GRANT_DENIED(
    "auth.permission.grant_denied",
    "Permission grant denies the request.",
  ),
  AUTH_PERMISSION_NO_MATCHING_GRANT(
    "auth.permission.no_matching_grant",
    "No active permission grant allows the request.",
  ),
  AUTH_PERMISSION_BINDING_DENIED(
    "auth.permission.binding_denied",
    "Policy binding denies the request.",
  ),
  AUTH_PERMISSION_NO_MATCHING_BINDING(
    "auth.permission.no_matching_binding",
    "No active policy binding allows the request.",
  ),
  SESSION_ACTIVE_NOT_FOUND("session.active.not_found", "Active session not found."),
  SESSION_TENANT_UPDATE_FAILED("session.tenant.update_failed", "Unable to update session tenant."),
  SESSION_SELECTED_TENANT_MISSING(
    "session.selected_tenant.missing",
    "Selected tenant no longer exists.",
  ),
  SESSION_CONTEXT_REQUIRED(
    "session.context_required",
    "Session context is required for tenant switching.",
  ),
  TENANT_NOT_SELECTED(
    "tenant.not_selected",
    "Select a tenant via PATCH /api/session before using tenant-scoped APIs.",
  ),
  TENANT_DESTROYING("tenant.destroying", "Tenant is being destroyed."),
  TENANT_ALREADY_DESTROYING("tenant.already_destroying", "Tenant is already being destroyed."),
  TENANT_DESTROYING_UPDATE_FORBIDDEN(
    "tenant.destroying.update_forbidden",
    "Tenant is being destroyed and cannot be updated.",
  ),
  TENANT_SLUG_IN_USE("tenant.slug.in_use", "Tenant slug is already in use."),
  TENANT_PENDING_ACTIVATION_REQUIRED(
    "tenant.pending_activation_required",
    "Tenant is not pending activation.",
  ),
  TENANT_MEMBER_INVITATION_UNSUPPORTED(
    "tenant.member_invitation_unsupported",
    "Tenant member invitations are not supported yet.",
  ),
  TENANT_CONFIG_DECODE_FAILED("tenant.config.decode_failed", "Tenant config cannot be decoded."),
  PROJECT_ALREADY_DESTROYING("project.already_destroying", "Project is already being destroyed."),
  PROJECT_DESTROYING("project.destroying", "Project is being destroyed."),
  PROJECT_ARCHIVED("project.archived", "Project is archived."),
  PROJECT_IDENTIFIER_IN_USE("project.identifier.in_use", "Project identifier is already in use."),
  PROJECT_SELF_JOIN_DISABLED(
    "project.member.self_join_disabled",
    "This project does not allow self-join.",
  ),
  PROJECT_MEMBER_INACTIVE_TENANT_MEMBER(
    "project.member.inactive_tenant_member",
    "User is not an active tenant member.",
  ),
  PROJECT_MEMBER_UNKNOWN_ROLE("project.member.unknown_role", "Unknown role."),
  PROJECT_MEMBER_POLICY_OR_ROLE_REQUIRED(
    "project.member.policy_or_role_required",
    "Either policyId or role is required.",
  ),
  INSTANCE_ALREADY_INITIALIZED("instance.already_initialized", "Instance is already initialized."),
  INSTANCE_SETUP_TOKEN_INVALID("instance.setup_token.invalid", "Setup token is invalid."),
  INSTANCE_SETUP_USER_ID_REQUIRED(
    "instance.setup.user_id_required",
    "userId is required when mode is USER.",
  ),
  INSTANCE_SETUP_EMAIL_REQUIRED(
    "instance.setup.email_required",
    "email is required when mode is EMAIL_INVITE.",
  ),
  IDENTITY_LOGIN_METHOD_DISABLED(
    "identity.login_method.disabled",
    "Login method is disabled for this tenant.",
  ),
  IDENTITY_LOGIN_METHOD_NOT_PASSWORD(
    "identity.login_method.not_password",
    "Login method is not password.",
  ),
  IDENTITY_LOGIN_METHOD_NOT_LDAP("identity.login_method.not_ldap", "Login method is not LDAP."),
  IDENTITY_LOGIN_METHOD_NOT_MAGIC_LINK(
    "identity.login_method.not_magic_link",
    "Login method is not email_magic_link.",
  ),
  IDENTITY_LOGIN_METHOD_UNSUPPORTED(
    "identity.login_method.unsupported",
    "Unsupported login method.",
  ),
  IDENTITY_LOGIN_SUBJECT_REQUIRED(
    "identity.login.subject_required",
    "subject is required for this login method.",
  ),
  IDENTITY_LOGIN_PASSWORD_REQUIRED(
    "identity.login.password_required",
    "password is required for this login method.",
  ),
  IDENTITY_LOGIN_TOKEN_REQUIRED(
    "identity.login.token_required",
    "token is required for api_token login.",
  ),
  IDENTITY_LOGIN_METHOD_ID_REQUIRED(
    "identity.login.login_method_id_required",
    "loginMethodId is required for this login method.",
  ),
  IDENTITY_LOGIN_TENANT_ID_REQUIRED(
    "identity.login.tenant_id_required",
    "tenantId is required for this login method.",
  ),
  IDENTITY_FEDERATED_PROTOCOL_UNSUPPORTED(
    "identity.federated.protocol_unsupported",
    "Login method does not support federated authorize.",
  ),
  IDENTITY_FEDERATED_OAUTH_STATE_INVALID(
    "identity.federated.oauth_state_invalid",
    "OAuth state is invalid or expired.",
  ),
  IDENTITY_FEDERATED_SAML_RELAY_STATE_INVALID(
    "identity.federated.saml_relay_state_invalid",
    "SAML relay state is invalid or expired.",
  ),
  IDENTITY_FEDERATED_LINKED_ACCOUNT_NOT_FOUND(
    "identity.federated.linked_account_not_found",
    "No linked account for federated identity.",
  ),
  IDENTITY_FEDERATED_LINKED_USER_NOT_FOUND(
    "identity.federated.linked_user_not_found",
    "No user linked for federated identity.",
  ),
  IDENTITY_FEDERATED_SUBJECT_UNRESOLVED(
    "identity.federated.subject_unresolved",
    "Unable to resolve federated subject.",
  ),
  IDENTITY_FEDERATED_SUBJECT_PARSE_FAILED(
    "identity.federated.subject_parse_failed",
    "Unable to parse federated subject.",
  ),
  IDENTITY_TENANT_LOGIN_SETTINGS_NOT_FOUND(
    "identity.tenant_login_settings.not_found",
    "Tenant login settings not found.",
  ),
  IDENTITY_MAGIC_LINK_DISABLED(
    "identity.magic_link.disabled",
    "Magic link login is disabled for this tenant.",
  ),
  IDENTITY_MAGIC_LINK_MAIL_NOT_CONFIGURED(
    "identity.magic_link.mail_not_configured",
    "Outbound mail is not configured for this tenant.",
  ),
  IDENTITY_MAGIC_LINK_INVALID("identity.magic_link.invalid", "Magic link is invalid or expired."),
  IDENTITY_MAGIC_LINK_NOT_CONFIGURED(
    "identity.magic_link.not_configured",
    "Magic link login is not configured.",
  ),
  IDENTITY_MAGIC_LINK_ACCOUNT_NOT_FOUND(
    "identity.magic_link.account_not_found",
    "No account is linked to this magic link.",
  ),
  IDENTITY_MAGIC_LINK_USER_NOT_FOUND(
    "identity.magic_link.user_not_found",
    "No user is linked to this magic link.",
  ),
  IDENTITY_OAUTH_CLIENT_ID_MISSING("identity.oauth.client_id_missing", "client_id missing."),
  IDENTITY_OAUTH_AUTHORIZATION_ENDPOINT_MISSING(
    "identity.oauth.authorization_endpoint_missing",
    "authorization endpoint missing in config.",
  ),
  IDENTITY_OAUTH_TOKEN_ENDPOINT_MISSING(
    "identity.oauth.token_endpoint_missing",
    "token_endpoint missing.",
  ),
  IDENTITY_OAUTH_TOKEN_EXCHANGE_FAILED(
    "identity.oauth.token_exchange_failed",
    "Token exchange failed.",
  ),
  IDENTITY_OAUTH_ACCESS_TOKEN_MISSING(
    "identity.oauth.access_token_missing",
    "access_token missing.",
  ),
  IDENTITY_OAUTH_USERINFO_ENDPOINT_MISSING(
    "identity.oauth.userinfo_endpoint_missing",
    "userinfo_endpoint missing.",
  ),
  IDENTITY_SAML_IDP_SSO_URL_MISSING(
    "identity.saml.idp_sso_url_missing",
    "idp_sso_url missing in SAML config.",
  ),
  INVITATION_INVALID_OR_EXPIRED(
    "invitation.invalid_or_expired",
    "Invitation is invalid or expired.",
  ),
  USER_EMAIL_ALREADY_EXISTS("user.email.already_exists", "A user with this email already exists."),
  PERMISSION_GROUP_BUILTIN_UPDATE_FORBIDDEN(
    "permission.group.builtin_update_forbidden",
    "Built-in groups cannot be updated.",
  ),
  PERMISSION_GROUP_BUILTIN_DELETE_FORBIDDEN(
    "permission.group.builtin_delete_forbidden",
    "Built-in groups cannot be deleted.",
  ),
  PERMISSION_POLICY_CODE_IN_USE("permission.policy.code_in_use", "Policy code is already in use."),
  PERMISSION_POLICY_BUILTIN_UPDATE_FORBIDDEN(
    "permission.policy.builtin_update_forbidden",
    "Built-in policies cannot be updated.",
  ),
  PERMISSION_POLICY_BUILTIN_DELETE_FORBIDDEN(
    "permission.policy.builtin_delete_forbidden",
    "Built-in policies cannot be deleted.",
  ),
  PERMISSION_POLICY_ACTIVE_BINDINGS(
    "permission.policy.active_bindings",
    "Policy has active bindings.",
  ),
  PERMISSION_POLICY_RULES_BUILTIN_CHANGE_FORBIDDEN(
    "permission.policy.rules_builtin_change_forbidden",
    "Built-in policy rules cannot be changed.",
  ),
  PERMISSION_BINDING_EFFECT_OVERRIDE_UNSUPPORTED(
    "permission.binding.effect_override_unsupported",
    "Binding effect overrides are not supported; use policy rules.",
  ),
  PERMISSION_BINDING_USER_TARGET_INVALID(
    "permission.binding.user_target_invalid",
    "USER binding requires userId only.",
  ),
  PERMISSION_BINDING_GROUP_TARGET_INVALID(
    "permission.binding.group_target_invalid",
    "GROUP binding requires groupId only.",
  ),
  PERMISSION_BINDING_TENANT_MEMBER_TARGET_INVALID(
    "permission.binding.tenant_member_target_invalid",
    "TENANT_MEMBER binding must not include userId or groupId.",
  ),
  WORK_ITEM_QUERY_INVALID_JSON(
    "work_item.query.parse.invalid_json",
    "Invalid work item query JSON.",
  ),
  WORK_ITEM_QUERY_LOGICAL_OPERATOR_UNKNOWN(
    "work_item.query.parse.logical_operator_unknown",
    "Unknown work item query logical operator.",
  ),
  WORK_ITEM_QUERY_SORT_DIRECTION_UNKNOWN(
    "work_item.query.sort.direction_unknown",
    "Unknown work item sort direction.",
  ),
  WORK_ITEM_QUERY_SORT_NULL_ORDERING_UNKNOWN(
    "work_item.query.sort.null_ordering_unknown",
    "Unknown work item sort null ordering.",
  ),
  WORK_ITEM_QUERY_FIELD_KIND_UNKNOWN(
    "work_item.query.field.kind_unknown",
    "Unknown work item query field kind.",
  ),
  WORK_ITEM_QUERY_FIELD_SHAPE_INVALID(
    "work_item.query.field.shape_invalid",
    "Work item query field must be a string or object.",
  ),
  WORK_ITEM_QUERY_PROPERTY_IDENTITY_REQUIRED(
    "work_item.query.field.property_identity_required",
    "Property query field must include an identity.",
  ),
  WORK_ITEM_QUERY_OPERATOR_UNKNOWN(
    "work_item.query.operator.unknown",
    "Unknown work item query operator.",
  ),
  WORK_ITEM_QUERY_RELATIVE_DATE_UNIT_UNKNOWN(
    "work_item.query.relative_date.unit_unknown",
    "Unknown relative date unit.",
  ),
  WORK_ITEM_QUERY_RELATIVE_DATE_DIRECTION_UNKNOWN(
    "work_item.query.relative_date.direction_unknown",
    "Unknown relative date direction.",
  ),
  WORK_ITEM_QUERY_OBJECT_REQUIRED(
    "work_item.query.parse.object_required",
    "Work item query value must be an object.",
  ),
  WORK_ITEM_QUERY_ARRAY_REQUIRED(
    "work_item.query.parse.array_required",
    "Work item query value must be an array.",
  ),
  WORK_ITEM_QUERY_STRING_REQUIRED(
    "work_item.query.parse.string_required",
    "Work item query value must be a string.",
  ),
  WORK_ITEM_QUERY_FIELD_REQUIRED(
    "work_item.query.parse.field_required",
    "Work item query missing required field.",
  ),
  WORK_ITEM_QUERY_INTEGER_REQUIRED(
    "work_item.query.parse.integer_required",
    "Work item query field must be an integer.",
  ),
  WORK_ITEM_QUERY_VERSION_UNSUPPORTED(
    "work_item.query.version_unsupported",
    "Unsupported work item query version.",
  ),
  WORK_ITEM_QUERY_RESOURCE_UNSUPPORTED(
    "work_item.query.resource_unsupported",
    "Unsupported query resource.",
  ),
  WORK_ITEM_QUERY_TOO_DEEPLY_NESTED(
    "work_item.query.too_deeply_nested",
    "Work item query is too deeply nested.",
  ),
  WORK_ITEM_QUERY_LOGICAL_EMPTY(
    "work_item.query.logical.empty",
    "Logical query nodes must contain at least one child.",
  ),
  WORK_ITEM_QUERY_OPERATOR_UNSUPPORTED_FOR_FIELD(
    "work_item.query.operator.unsupported_for_field",
    "Operator is not supported by field.",
  ),
  WORK_ITEM_QUERY_FIELD_NOT_SORTABLE(
    "work_item.query.sort.field_not_sortable",
    "Field is not sortable.",
  ),
  WORK_ITEM_QUERY_TOO_MANY_PREDICATES(
    "work_item.query.too_many_predicates",
    "Work item query has too many predicates.",
  ),
  WORK_ITEM_QUERY_OPERATOR_VALUE_FORBIDDEN(
    "work_item.query.operator.value_forbidden",
    "Operator does not accept a value.",
  ),
  WORK_ITEM_QUERY_OPERATOR_VALUE_REQUIRED(
    "work_item.query.operator.value_required",
    "Operator requires a value.",
  ),
  WORK_ITEM_QUERY_OPERATOR_ARRAY_REQUIRED(
    "work_item.query.operator.array_required",
    "Operator requires an array value.",
  ),
  WORK_ITEM_QUERY_OPERATOR_ARRAY_NOT_EMPTY_REQUIRED(
    "work_item.query.operator.array_not_empty_required",
    "Operator requires a non-empty array value.",
  ),
  WORK_ITEM_QUERY_OPERATOR_BETWEEN_OBJECT_REQUIRED(
    "work_item.query.operator.between_object_required",
    "Operator between requires an object value.",
  ),
  WORK_ITEM_QUERY_OPERATOR_BETWEEN_BOUND_REQUIRED(
    "work_item.query.operator.between_bound_required",
    "Operator between requires from or to.",
  ),
  WORK_ITEM_QUERY_OPERATOR_RELATIVE_DATE_REQUIRED(
    "work_item.query.operator.relative_date_required",
    "Operator within requires a relativeDate value.",
  ),
  WORK_ITEM_QUERY_RELATIVE_DATE_AMOUNT_POSITIVE(
    "work_item.query.relative_date.amount_positive",
    "Relative date amount must be positive.",
  ),
  WORK_ITEM_QUERY_RELATIVE_DATE_ANCHOR_UNKNOWN(
    "work_item.query.relative_date.anchor_unknown",
    "Unknown relative date anchor.",
  ),
  WORK_ITEM_QUERY_OPERATOR_STRING_REQUIRED(
    "work_item.query.operator.string_required",
    "Operator requires a string value.",
  ),
  WORK_ITEM_QUERY_VARIABLE_UNKNOWN(
    "work_item.query.variable.unknown",
    "Unknown work item query variable.",
  ),
  WORK_ITEM_QUERY_OPERATOR_SINGLE_VALUE_REQUIRED(
    "work_item.query.operator.single_value_required",
    "Operator requires a single value.",
  ),
  WORK_ITEM_QUERY_FIELD_UNKNOWN(
    "work_item.query.field.unknown",
    "Unknown work item query field.",
  ),
  WORK_ITEM_QUERY_PROPERTY_UNKNOWN(
    "work_item.query.property.unknown",
    "Unknown work item query property.",
  ),
  WORK_ITEM_QUERY_PROPERTY_TYPE_UNSUPPORTED(
    "work_item.query.property.type_unsupported",
    "Unsupported work item property type.",
  ),
  WORK_ITEM_QUERY_LONG_TEXT_REQUIRES_ELASTICSEARCH(
    "work_item.query.runtime.long_text_requires_elasticsearch",
    "Long text work item predicates require Elasticsearch.",
  ),
  WORK_ITEM_QUERY_VARIABLE_TRUSTED_CONTEXT_REQUIRED(
    "work_item.query.variable.trusted_context_required",
    "Variable requires trusted request context binding.",
  ),
  RESOURCE_WORK_ITEM_STATUS_NOT_FOUND(
    "resource.work_item_status.not_found",
    "Work item status not found.",
  ),
  RESOURCE_WORK_ITEM_PROPERTY_NOT_FOUND(
    "resource.work_item_property.not_found",
    "Work item property not found.",
  ),
  RESOURCE_WORK_ITEM_TYPE_NOT_FOUND(
    "resource.work_item_type.not_found",
    "Work item type not found.",
  ),
  RESOURCE_WORKFLOW_NOT_FOUND("resource.workflow.not_found", "Workflow not found."),
  RESOURCE_WORKFLOW_TRANSITION_NOT_FOUND(
    "resource.workflow_transition.not_found",
    "Workflow transition not found.",
  ),
  RESOURCE_WORK_ITEM_TYPE_CONFIG_NOT_FOUND(
    "resource.work_item_type_config.not_found",
    "Work item type config not found.",
  ),
  WORK_ITEM_CONFIG_SCOPE_UNKNOWN(
    "work_item.config.scope_unknown",
    "Unknown work item config scope.",
  ),
  WORK_ITEM_STATUS_GROUP_UNKNOWN(
    "work_item.status.group_unknown",
    "Unknown work item status group.",
  ),
  WORK_ITEM_PROPERTY_TYPE_UNKNOWN(
    "work_item.property.type_unknown",
    "Unknown work item property data type.",
  ),
  WORK_ITEM_CONFIG_SCOPE_PROJECT_REQUIRED(
    "work_item.config.scope_project_required",
    "Project scope requires a project id.",
  ),
  WORK_ITEM_CONFIG_SCOPE_PROJECT_FORBIDDEN(
    "work_item.config.scope_project_forbidden",
    "Tenant scope must not include a project id.",
  ),
  WORK_ITEM_CONFIG_INITIAL_STATUS_REQUIRED(
    "work_item.config.initial_status_required",
    "Exactly one initial status is required.",
  ),
  WORK_ITEM_CONFIG_DUPLICATE_STATUS(
    "work_item.config.duplicate_status",
    "Status is bound more than once.",
  ),
  WORK_ITEM_CONFIG_DUPLICATE_PROPERTY(
    "work_item.config.duplicate_property",
    "Property is bound more than once.",
  ),
  WORK_ITEM_CONFIG_CROSS_TENANT_REFERENCE(
    "work_item.config.cross_tenant_reference",
    "Referenced work item configuration resource belongs to another tenant.",
  ),
  WORKFLOW_PUBLISHED_UPDATE_FORBIDDEN(
    "workflow.published_update_forbidden",
    "Published workflows cannot be updated in place.",
  ),
  WORKFLOW_TRANSITION_STATUS_UNAVAILABLE(
    "workflow.transition.status_unavailable",
    "Transition status is not available in the effective type config.",
  ),
  DOMAIN_EVENT_ENVELOPE_INVALID_JSON(
    "domain_event.envelope.invalid_json",
    "Invalid domain event envelope JSON.",
  ),
  DOMAIN_EVENT_TYPE_MISMATCH("domain_event.type_mismatch", "Domain event type mismatch."),
  DOMAIN_EVENT_VERSION_UNSUPPORTED(
    "domain_event.version_unsupported",
    "Unsupported domain event version.",
  ),
  DOMAIN_EVENT_PAYLOAD_DECODE_FAILED(
    "domain_event.payload.decode_failed",
    "Domain event payload cannot be decoded.",
  ),
  REQUEST_INVALID("request.invalid", "Invalid request."),
  REQUEST_VALIDATION_FAILED("request.validation_failed", "Validation failed."),
  REQUEST_PROJECT_ID_REQUIRED(
    "request.project_id_required",
    "Project id path variable is required.",
  ),
  INFRASTRUCTURE_DATABASE_UNAVAILABLE(
    "infrastructure.database_unavailable",
    "The database is temporarily unavailable.",
  ),
  INFRASTRUCTURE_UNAVAILABLE("infrastructure.unavailable", "Infrastructure is unavailable.");

  init {
    require(code.matches(WorkbenchErrorCodePattern)) {
      "Workbench error code must be dot-separated lower-case words."
    }
  }

  companion object {
    private val AuthorizationReasonCodes =
      mapOf(
        "missing_instance_admin" to AUTH_PERMISSION_MISSING_INSTANCE_ADMIN,
        "token_scope_denied" to AUTH_PERMISSION_TOKEN_SCOPE_DENIED,
        "missing_tenant" to AUTH_PERMISSION_MISSING_TENANT,
        "missing_membership" to AUTH_PERMISSION_MISSING_MEMBERSHIP,
        "inactive_membership" to AUTH_PERMISSION_INACTIVE_MEMBERSHIP,
        "grant_denied" to AUTH_PERMISSION_GRANT_DENIED,
        "no_matching_grant" to AUTH_PERMISSION_NO_MATCHING_GRANT,
        "binding_denied" to AUTH_PERMISSION_BINDING_DENIED,
        "no_matching_binding" to AUTH_PERMISSION_NO_MATCHING_BINDING,
      )

    fun fromAuthorizationReason(code: String): WorkbenchErrorCode =
      AuthorizationReasonCodes[code] ?: AUTH_PERMISSION_DENIED
  }
}
