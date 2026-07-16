package ink.doa.workbench.kernel.common.errors

private val WorkbenchErrorCodePattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

enum class WorkbenchErrorCode(
  val code: String,
  val defaultMessage: String,
) {
  RESOURCE_TENANT_NOT_FOUND("resource.tenant.not_found", "Tenant not found."),
  RESOURCE_PROJECT_NOT_FOUND("resource.project.not_found", "Project not found."),
  RESOURCE_USER_NOT_FOUND("resource.user.not_found", "User not found."),
  RESOURCE_ADMIN_USER_NOT_FOUND("resource.admin_user.not_found", "Admin user not found."),
  RESOURCE_TENANT_MEMBER_NOT_FOUND("resource.tenant_member.not_found", "Tenant member not found."),
  RESOURCE_INVITATION_NOT_FOUND("resource.invitation.not_found", "Invitation not found."),
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
  INSTANCE_LAST_ADMIN_REQUIRED(
    "instance.last_admin.required",
    "The last active instance administrator cannot be removed.",
  ),
  TENANT_LAST_ADMIN_REQUIRED(
    "tenant.last_admin.required",
    "The last active tenant administrator cannot be removed.",
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
  TENANT_MEMBER_INVITATION_AUTHENTICATED_ACCEPTANCE_REQUIRED(
    "tenant.member_invitation.authenticated_acceptance_required",
    "Sign in with the invited account to accept this invitation.",
  ),
  TENANT_MEMBER_INVITATION_EMAIL_MISMATCH(
    "tenant.member_invitation.email_mismatch",
    "The signed-in account does not match the invitation email.",
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
  PERMISSION_POLICY_RULE_CONDITION_INVALID(
    "permission.policy.rule_condition_invalid",
    "Policy rule condition must be valid JSON condition AST.",
  ),
  PERMISSION_POLICY_REVISION_CONFLICT(
    "permission.policy.revision_conflict",
    "Permission policy was changed by another editor.",
  ),
  PERMISSION_POLICY_RULE_ID_INVALID(
    "permission.policy.rule_id_invalid",
    "Policy rule id does not belong to this policy.",
  ),
  PERMISSION_POLICY_RULE_ACTION_UNKNOWN(
    "permission.policy.rule_action_unknown",
    "Policy rule action is not registered.",
  ),
  PERMISSION_POLICY_RULE_RESOURCE_PATTERN_INVALID(
    "permission.policy.rule_resource_pattern_invalid",
    "Resource pattern must be '*', an exact resource, or a prefix ending in ':*'.",
  ),
  PERMISSION_POLICY_TENANT_RULE_REQUIRED(
    "permission.policy.tenant_rule_required",
    "Tenant permission policies require at least one rule.",
  ),
  PERMISSION_POLICY_TENANT_ACTION_FORBIDDEN(
    "permission.policy.tenant_action_forbidden",
    "Action is not available to tenant permission policies.",
  ),
  PERMISSION_POLICY_TENANT_RESOURCE_MISMATCH(
    "permission.policy.tenant_resource_mismatch",
    "Resource pattern does not match the selected tenant capability.",
  ),
  PERMISSION_POLICY_TENANT_CONDITION_FORBIDDEN(
    "permission.policy.tenant_condition_forbidden",
    "Tenant permission policies do not support conditions.",
  ),
  PERMISSION_POLICY_NOT_TENANT_POLICY(
    "permission.policy.not_tenant_policy",
    "Policy is not a tenant management policy.",
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
  PERMISSION_BINDING_EXPIRATION_INVALID(
    "permission.binding.expiration_invalid",
    "Permission binding expiration must be in the future.",
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
  WORK_ITEM_QUERY_GROUP_REQUIRED(
    "work_item.query.group.required",
    "Work item query group is required.",
  ),
  WORK_ITEM_QUERY_FIELD_NOT_GROUPABLE(
    "work_item.query.field.not_groupable",
    "Field is not groupable.",
  ),
  WORK_ITEM_QUERY_GROUP_KEY_OPERATOR_UNSUPPORTED(
    "work_item.query.group_key.operator_unsupported",
    "Unsupported work item group key operator.",
  ),
  WORK_ITEM_QUERY_GROUP_KEY_FIELD_MISMATCH(
    "work_item.query.group_key.field_mismatch",
    "Group key field must match query group field.",
  ),
  WORK_ITEM_SEARCH_SCOPE_CONFLICT(
    "work_item.search.scope.conflict",
    "Work item search scope cannot combine include and exclude group keys.",
  ),
  WORK_ITEM_SEARCH_CURSOR_INVALID(
    "work_item.search.cursor.invalid",
    "Invalid work item search cursor.",
  ),
  WORK_ITEM_SEARCH_GROUP_CURSOR_INVALID(
    "work_item.search.group_cursor.invalid",
    "Invalid work item search group cursor.",
  ),
  WORK_ITEM_SEARCH_CURSOR_SORT_MISMATCH(
    "work_item.search.cursor.sort_mismatch",
    "Work item search cursor does not match query sort stack.",
  ),
  WORK_ITEM_TEMPLATE_INVALID_JSON(
    "work_item.template.parse.invalid_json",
    "Invalid work item value template JSON.",
  ),
  WORK_ITEM_TEMPLATE_OBJECT_REQUIRED(
    "work_item.template.parse.object_required",
    "Work item value template value must be an object.",
  ),
  WORK_ITEM_TEMPLATE_STRING_REQUIRED(
    "work_item.template.parse.string_required",
    "Work item value template value must be a string.",
  ),
  WORK_ITEM_TEMPLATE_INTEGER_REQUIRED(
    "work_item.template.parse.integer_required",
    "Work item value template field must be an integer.",
  ),
  WORK_ITEM_TEMPLATE_FIELD_REQUIRED(
    "work_item.template.parse.field_required",
    "Work item value template missing required field.",
  ),
  WORK_ITEM_TEMPLATE_TARGET_UNKNOWN(
    "work_item.template.target_unknown",
    "Unknown work item value template target.",
  ),
  WORK_ITEM_TEMPLATE_VERSION_UNSUPPORTED(
    "work_item.template.version_unsupported",
    "Unsupported work item value template version.",
  ),
  WORK_ITEM_TEMPLATE_RESOURCE_UNSUPPORTED(
    "work_item.template.resource_unsupported",
    "Unsupported work item value template resource.",
  ),
  WORK_ITEM_TEMPLATE_PROPERTY_IDENTITY_REQUIRED(
    "work_item.template.field.property_identity_required",
    "Property template field must include an identity.",
  ),
  WORK_ITEM_TEMPLATE_FIELD_NOT_WRITABLE(
    "work_item.template.field.not_writable",
    "Field is not writable by work item value templates.",
  ),
  WORK_ITEM_TEMPLATE_VARIABLE_UNKNOWN(
    "work_item.template.variable.unknown",
    "Unknown work item value template variable.",
  ),
  WORK_ITEM_TEMPLATE_RELATIVE_DATE_UNIT_UNKNOWN(
    "work_item.template.relative_date.unit_unknown",
    "Unknown relative date unit.",
  ),
  WORK_ITEM_TEMPLATE_RELATIVE_DATE_DIRECTION_UNKNOWN(
    "work_item.template.relative_date.direction_unknown",
    "Unknown relative date direction.",
  ),
  WORK_ITEM_TEMPLATE_RELATIVE_DATE_AMOUNT_POSITIVE(
    "work_item.template.relative_date.amount_positive",
    "Relative date amount must be positive.",
  ),
  WORK_ITEM_TEMPLATE_RELATIVE_DATE_ANCHOR_UNKNOWN(
    "work_item.template.relative_date.anchor_unknown",
    "Unknown relative date anchor.",
  ),
  WORK_ITEM_TEMPLATE_CLEAR_REQUIRED_FIELD(
    "work_item.template.clear_required_field",
    "Required field cannot be cleared by a work item value template.",
  ),
  WORK_ITEM_TEMPLATE_VALUE_TYPE_INVALID(
    "work_item.template.value_type_invalid",
    "Work item value template expression is not compatible with the target field.",
  ),
  WORK_ITEM_TEMPLATE_TOO_MANY_VALUES(
    "work_item.template.too_many_values",
    "Work item value template has too many values.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_INVALID_JSON(
    "work_item.transition_fields.invalid_json",
    "Invalid transition fields JSON.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_OBJECT_REQUIRED(
    "work_item.transition_fields.object_required",
    "Transition fields payload must be an object.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_STRING_REQUIRED(
    "work_item.transition_fields.string_required",
    "Transition fields value must be a string.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_INTEGER_REQUIRED(
    "work_item.transition_fields.integer_required",
    "Transition fields value must be an integer.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_FIELD_REQUIRED(
    "work_item.transition_fields.field_required",
    "Transition fields payload is missing a required field.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_VERSION_UNSUPPORTED(
    "work_item.transition_fields.version_unsupported",
    "Unsupported transition fields version.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_RESOURCE_UNSUPPORTED(
    "work_item.transition_fields.resource_unsupported",
    "Unsupported transition fields resource.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_TARGET_INVALID(
    "work_item.transition_fields.target_invalid",
    "Transition fields target must be transition.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_PARTICIPATION_UNKNOWN(
    "work_item.transition_fields.participation_unknown",
    "Unknown transition field participation.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_WRITE_GRANT_UNKNOWN(
    "work_item.transition_fields.write_grant_unknown",
    "Unknown transition field write grant.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_ON_UNAUTHORIZED_UNKNOWN(
    "work_item.transition_fields.on_unauthorized_unknown",
    "Unknown transition field onUnauthorized behavior.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_TOO_MANY(
    "work_item.transition_fields.too_many",
    "Transition fields template has too many fields.",
  ),
  WORK_ITEM_TRANSITION_FIELDS_AUTOMATIC_VALUE_REQUIRED(
    "work_item.transition_fields.automatic_value_required",
    "Automatic transition field requires a value expression.",
  ),
  WORK_ITEM_TRANSITION_COMMENT_PARTICIPATION_INVALID(
    "work_item.transition.comment.participation_invalid",
    "Transition comment participation is invalid.",
  ),
  WORK_ITEM_TRANSITION_COMMENT_TEMPLATE_REQUIRED(
    "work_item.transition.comment.template_required",
    "Required transition comment must define a template.",
  ),
  WORK_ITEM_TRANSITION_COMMENT_TEMPLATE_MUST_BE_PLAIN_TEXT(
    "work_item.transition.comment.template_must_be_plain_text",
    "Transition comment template must be plain text.",
  ),
  WORK_ITEM_TRANSITION_COMMENT_REQUIRED(
    "work_item.transition.comment.required",
    "Transition comment is required.",
  ),
  WORK_ITEM_DESCRIPTION_TEMPLATE_MUST_BE_PLAIN_TEXT(
    "work_item.description.template_must_be_plain_text",
    "Description template must be plain text.",
  ),
  WORK_ITEM_COMMENT_BODY_REQUIRED(
    "work_item.comment.body_required",
    "Comment body is required.",
  ),
  WORK_ITEM_COMMENT_BODY_TOO_LONG(
    "work_item.comment.body_too_long",
    "Comment body is too long.",
  ),
  WORK_ITEM_COMMENT_CREATE_DENIED(
    "work_item.comment.create_denied",
    "Work item comment create permission denied.",
  ),
  WORK_ITEM_COMMENT_UPDATE_DENIED(
    "work_item.comment.update_denied",
    "Work item comment update permission denied.",
  ),
  WORK_ITEM_COMMENT_DELETE_DENIED(
    "work_item.comment.delete_denied",
    "Work item comment delete permission denied.",
  ),
  WORK_ITEM_ACTIVITY_PAYLOAD_INVALID(
    "work_item.activity.payload_invalid",
    "Work item activity payload is invalid.",
  ),
  WORK_ITEM_CREATE_FIELDS_REQUIRED(
    "work_item.create_fields.required",
    "Create fields template is required and must define at least one field.",
  ),
  WORK_ITEM_CREATE_FIELDS_TARGET_INVALID(
    "work_item.create_fields.target_invalid",
    "Create fields target must be create.",
  ),
  WORKFLOW_TRANSITION_FROM_STATUS_INVALID(
    "workflow.transition.from_status_invalid",
    "Workflow transition requires a valid from status unless it is global.",
  ),
  WORK_ITEM_FIELD_WRITE_DENIED(
    "work_item.field.write_denied",
    "Work item field write permission denied.",
  ),
  WORK_ITEM_TRANSITION_FIELD_IMMUTABLE_BUT_REQUIRED(
    "work_item.transition.field_immutable_but_required",
    "Required transition field is immutable and has no value.",
  ),
  WORK_ITEM_TRANSITION_UNAUTHORIZED_FIELD_MUTATION(
    "work_item.transition.unauthorized_field_mutation",
    "Unauthorized field mutation during transition.",
  ),
  WORK_ITEM_MUTATION_UNEXPECTED_FIELD(
    "work_item.mutation.unexpected_field",
    "Unexpected field in work item mutation request.",
  ),
  WORK_ITEM_MUTATION_FIELD_NOT_EDITABLE(
    "work_item.mutation.field_not_editable",
    "Field is not editable in this mutation request.",
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
  RESOURCE_WORK_ITEM_SUBTYPE_CONSTRAINT_NOT_FOUND(
    "resource.work_item_subtype_constraint.not_found",
    "Work item subtype constraint not found.",
  ),
  RESOURCE_WORK_ITEM_NOT_FOUND("resource.work_item.not_found", "Work item not found."),
  RESOURCE_WORK_ITEM_COMMENT_NOT_FOUND(
    "resource.work_item_comment.not_found",
    "Work item comment not found.",
  ),
  RESOURCE_WORK_ITEM_ATTACHMENT_NOT_FOUND(
    "resource.work_item_attachment.not_found",
    "Work item attachment not found.",
  ),
  RESOURCE_WORK_ITEM_VIEW_NOT_FOUND(
    "resource.work_item_view.not_found",
    "Work item view not found.",
  ),
  RESOURCE_SPRINT_NOT_FOUND("resource.sprint.not_found", "Sprint not found."),
  SPRINT_STATUS_INVALID_TRANSITION(
    "sprint.status.invalid_transition",
    "Sprint status transition is not allowed.",
  ),
  SPRINT_ACTIVE_CONFLICT(
    "sprint.active.conflict",
    "Project already has an active sprint.",
  ),
  SPRINT_CLOSED_IMMUTABLE(
    "sprint.closed.immutable",
    "Closed sprint cannot be modified.",
  ),
  SPRINT_CLOSING("sprint.closing", "Sprint is being closed asynchronously."),
  SPRINT_CLOSE_OPERATION_CONFLICT(
    "sprint.close_operation.conflict",
    "Sprint already has a close operation in progress.",
  ),
  SPRINT_CLOSE_TARGET_REQUIRED(
    "sprint.close.target_required",
    "A target sprint is required for this disposition.",
  ),
  SPRINT_CLOSE_TARGET_INVALID(
    "sprint.close.target_invalid",
    "The target sprint is invalid for this close operation.",
  ),
  SPRINT_CLOSE_OPERATION_NOT_FOUND(
    "sprint.close_operation.not_found",
    "Sprint close operation not found.",
  ),
  SPRINT_DATE_RANGE_INVALID(
    "sprint.date_range.invalid",
    "Sprint end date must be on or after the start date.",
  ),
  WORK_ITEM_VIEW_READ_DENIED(
    "work_item.view.read_denied",
    "Work item view read permission denied.",
  ),
  WORK_ITEM_VIEW_CREATE_DENIED(
    "work_item.view.create_denied",
    "Work item view create permission denied.",
  ),
  WORK_ITEM_VIEW_MANAGE_DENIED(
    "work_item.view.manage_denied",
    "Work item view manage permission denied.",
  ),
  WORK_ITEM_VIEW_VISIBILITY_UNKNOWN(
    "work_item.view.visibility_unknown",
    "Unknown work item view visibility.",
  ),
  WORK_ITEM_VIEW_VISIBILITY_PROJECT_FORBIDDEN(
    "work_item.view.visibility_project_forbidden",
    "Tenant-scoped views cannot use project visibility.",
  ),
  WORK_ITEM_VIEW_LAYOUT_OBJECT_REQUIRED(
    "work_item.view.layout_object_required",
    "Work item view layout value must be an object.",
  ),
  WORK_ITEM_VIEW_LAYOUT_ARRAY_REQUIRED(
    "work_item.view.layout_array_required",
    "Work item view layout value must be an array.",
  ),
  WORK_ITEM_VIEW_LAYOUT_STRING_REQUIRED(
    "work_item.view.layout_string_required",
    "Work item view layout value must be a string.",
  ),
  WORK_ITEM_VIEW_LAYOUT_FIELD_REQUIRED(
    "work_item.view.layout_field_required",
    "Work item view layout is missing a required field.",
  ),
  WORK_ITEM_VIEW_GROUP_DIRECTION_UNKNOWN(
    "work_item.view.group_direction_unknown",
    "Unknown work item view group direction.",
  ),
  WORK_ITEM_VIEW_DISPLAY_FIELD_WIDTH_INVALID(
    "work_item.view.display_field_width_invalid",
    "Work item view display field width must be an integer.",
  ),
  WORK_ITEM_ATTACHMENT_CREATE_DENIED(
    "work_item.attachment.create_denied",
    "Work item attachment create permission denied.",
  ),
  WORK_ITEM_ATTACHMENT_DELETE_DENIED(
    "work_item.attachment.delete_denied",
    "Work item attachment delete permission denied.",
  ),
  WORK_ITEM_ATTACHMENT_TOO_LARGE(
    "work_item.attachment.too_large",
    "Work item attachment exceeds the maximum allowed size.",
  ),
  WORK_ITEM_ATTACHMENT_CONTENT_TYPE_UNSUPPORTED(
    "work_item.attachment.content_type_unsupported",
    "Work item attachment content type is not supported.",
  ),
  WORK_ITEM_ATTACHMENT_COMMENT_REQUIRED(
    "work_item.attachment.comment_required",
    "Comment attachment requires a comment id.",
  ),
  WORK_ITEM_ATTACHMENT_FILE_REQUIRED(
    "work_item.attachment.file_required",
    "Attachment file is required.",
  ),
  WORK_ITEM_ATTACHMENT_PURPOSE_INVALID(
    "work_item.attachment.purpose_invalid",
    "Work item attachment purpose is invalid.",
  ),
  WORK_ITEM_ATTACHMENT_UPLOAD_INCOMPLETE(
    "work_item.attachment.upload_incomplete",
    "Work item attachment upload has not been completed in object storage.",
  ),
  WORK_ITEM_ATTACHMENT_UPLOAD_SIZE_MISMATCH(
    "work_item.attachment.upload_size_mismatch",
    "Uploaded object size does not match the declared file size.",
  ),
  WORK_ITEM_ATTACHMENT_UPLOAD_EXPIRED(
    "work_item.attachment.upload_expired",
    "Work item attachment upload session has expired.",
  ),
  WORK_ITEM_DESCRIPTION_ATTACHMENT_REFERENCE_INVALID(
    "work_item.description.attachment_reference_invalid",
    "Description references an invalid work item attachment.",
  ),
  WORK_ITEM_DESCRIPTION_ATTACHMENT_CREATE_UNSUPPORTED(
    "work_item.description.attachment_create_unsupported",
    "Description attachments are not supported during work item creation.",
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
  WORK_ITEM_CONFIG_RESOURCE_IN_USE(
    "work_item.config.resource_in_use",
    "Work item configuration resource is still used by active configuration.",
  ),
  WORK_ITEM_SUBTYPE_CONSTRAINT_INVALID(
    "work_item.subtype_constraint.invalid",
    "Work item subtype constraint is invalid.",
  ),
  WORK_ITEM_SUBTYPE_NOT_ALLOWED(
    "work_item.subtype.not_allowed",
    "Work item type is not allowed under the selected parent type.",
  ),
  WORK_ITEM_SUBTYPE_PARENT_REQUIRED(
    "work_item.subtype.parent_required",
    "This work item type must be created under a parent work item.",
  ),
  WORK_ITEM_SUBTYPE_CROSS_PROJECT_FORBIDDEN(
    "work_item.subtype.cross_project_forbidden",
    "Cross-project child work items are not allowed.",
  ),
  WORKFLOW_PUBLISHED_UPDATE_FORBIDDEN(
    "workflow.published_update_forbidden",
    "Published workflows cannot be updated in place.",
  ),
  WORKFLOW_TRANSITION_STATUS_UNAVAILABLE(
    "workflow.transition.status_unavailable",
    "Transition status is not available in the effective type config.",
  ),
  WORK_ITEM_PROPERTY_REQUIRED(
    "work_item.property.required",
    "Required work item property is missing.",
  ),
  WORK_ITEM_PROPERTY_UNAVAILABLE(
    "work_item.property.unavailable",
    "Property is not available for this work item type config.",
  ),
  WORK_ITEM_PROPERTY_VALUE_INVALID(
    "work_item.property.value_invalid",
    "Work item property value is invalid.",
  ),
  WORK_ITEM_TRANSITION_STATUS_MISMATCH(
    "work_item.transition.status_mismatch",
    "Transition is not valid from the current status.",
  ),
  WORK_ITEM_TRANSITION_PERMISSION_DENIED(
    "work_item.transition.permission_denied",
    "Transition permission condition denied the request.",
  ),
  WORK_ITEM_TRANSITION_PRECONDITION_FAILED(
    "work_item.transition.precondition_failed",
    "Transition precondition is not satisfied.",
  ),
  WORK_ITEM_CONDITION_UNSUPPORTED(
    "work_item.condition.unsupported",
    "Unsupported work item condition expression.",
  ),
  WORK_ITEM_CONDITION_LEGACY_SYNTAX(
    "work_item.condition.legacy_syntax",
    "Legacy condition syntax is not supported.",
  ),
  WORK_ITEM_CONDITION_UUID_LITERAL_FORBIDDEN(
    "work_item.condition.uuid_literal_forbidden",
    "Condition literals for entity identifiers must use public apiId values, not UUIDs.",
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
  OUTBOX_MESSAGE_NOT_FOUND("outbox.message.not_found", "Outbox message was not found."),
  OUTBOX_DELIVERY_REPLAY_NOT_ALLOWED(
    "outbox.delivery.replay.not_allowed",
    "Only dead-letter outbox deliveries can be replayed.",
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
