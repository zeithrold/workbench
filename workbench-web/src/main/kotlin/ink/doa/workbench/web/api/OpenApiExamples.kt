package ink.doa.workbench.web.api

/**
 * Canonical public ids and JSON example payloads for OpenAPI documentation. Values align with
 * [.agents/skills/api-design/examples.md].
 */
object OpenApiExamples {
  const val TENANT_ID = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0"
  const val TENANT_ID_OTHER = "ten_01JOTHERABCDEFGHJKMNPQRSTVW"
  const val USER_ID = "usr_01JABCDEFGHJKMNPQRSTVWXYZ0"
  const val PROJECT_ID = "prj_01JABCDEFGHJKMNPQRSTVWXYZ0"
  const val ROLE_ID = "rol_01JABCDEFGHJKMNPQRSTVWXYZ0"
  const val POLICY_ID = "pol_01JABCDEFGHJKMNPQRSTVWXYZ0"
  const val ASSIGNMENT_ID = "ras_01JABCDEFGHJKMNPQRSTVWXYZ0"
  const val LOGIN_METHOD_ID = "lmg_01JABCDEFGHJKMNPQRSTVWXYZ0"
  const val MEMBERSHIP_ID = "tmb_01JABCDEFGHJKMNPQRSTVWXYZ0"
  const val BEARER_TOKEN_ID = "btk_01JABCDEFGHJKMNPQRSTVWXYZ0"

  const val VALIDATION_FAILED =
    """
    {
      "type": "https://api.ink.doa/workbench/problems/validation-failed",
      "title": "Validation Failed",
      "status": 400,
      "detail": "identifier: must match \"^[A-Z][A-Z0-9]{1,9}$\"",
      "code": "request.validation_failed"
    }
    """

  const val INVALID_REQUEST =
    """
    {
      "type": "https://api.ink.doa/workbench/problems/invalid-request",
      "title": "Invalid Request",
      "status": 400,
      "detail": "Project identifier is already in use.",
      "code": "project.identifier.in_use"
    }
    """

  const val AUTHENTICATION_FAILED =
    """
    {
      "type": "https://api.ink.doa/workbench/problems/authentication-failed",
      "title": "Authentication Failed",
      "status": 401,
      "detail": "Invalid credentials.",
      "code": "auth.invalid_credentials"
    }
    """

  const val PERMISSION_DENIED =
    """
    {
      "type": "https://api.ink.doa/workbench/problems/permission-denied",
      "title": "Permission Denied",
      "status": 403,
      "detail": "Actor lacks permission.project.create on project.",
      "code": "auth.permission.no_matching_binding"
    }
    """

  const val RESOURCE_NOT_FOUND =
    """
    {
      "type": "https://api.ink.doa/workbench/problems/resource-not-found",
      "title": "Resource Not Found",
      "status": 404,
      "detail": "Project not found.",
      "code": "resource.project.not_found"
    }
    """

  const val TENANT_NOT_SELECTED =
    """
    {
      "type": "https://api.ink.doa/workbench/problems/tenant-not-selected",
      "title": "Tenant Not Selected",
      "status": 409,
      "detail": "Select a tenant via PATCH /api/session before using tenant-scoped APIs.",
      "code": "tenant.not_selected"
    }
    """

  const val PROJECT_CREATED =
    """
    {
      "id": "$PROJECT_ID",
      "identifier": "CORE",
      "name": "Core Platform",
      "description": "Platform engineering."
    }
    """

  const val PROJECT_LIST =
    """
    [
      {
        "id": "$PROJECT_ID",
        "identifier": "CORE",
        "name": "Core Platform",
        "description": "Platform engineering."
      }
    ]
    """

  const val LOGIN_REQUEST_PASSWORD =
    """
    {
      "method": "PASSWORD",
      "tenantId": "$TENANT_ID",
      "loginMethodId": "$LOGIN_METHOD_ID",
      "subject": "user@example.com",
      "password": "secret",
      "issueBearerToken": false
    }
    """

  const val LOGIN_SUCCESS =
    """
    {
      "user": {
        "id": "$USER_ID",
        "displayName": "Jane Doe",
        "primaryEmail": "jane@example.com"
      },
      "sessionExpiresAt": "2026-07-02T12:00:00+00:00",
      "bearerToken": null
    }
    """

  const val LOGIN_SUCCESS_WITH_BEARER =
    """
    {
      "user": {
        "id": "$USER_ID",
        "displayName": "Jane Doe",
        "primaryEmail": "jane@example.com"
      },
      "sessionExpiresAt": "2026-07-02T12:00:00+00:00",
      "bearerToken": {
        "id": "$BEARER_TOKEN_ID",
        "token": "wbk_live_01JABCDEFGHJKMNPQRSTVWXYZ0",
        "expiresAt": "2027-07-02T12:00:00+00:00"
      }
    }
    """

  const val SESSION_ACTIVE =
    """
    {
      "user": {
        "id": "$USER_ID",
        "displayName": "Jane Doe",
        "primaryEmail": "jane@example.com"
      },
      "activeTenant": {
        "id": "$TENANT_ID",
        "name": "Acme Corp",
        "slug": "acme"
      },
      "sessionExpiresAt": "2026-07-02T12:00:00+00:00"
    }
    """

  const val SWITCH_TENANT_REQUEST =
    """
  { "tenantId": "$TENANT_ID_OTHER" }
    """

  const val MEMBERSHIP_LIST =
    """
    [
      {
        "id": "$MEMBERSHIP_ID",
        "tenant": {
          "id": "$TENANT_ID",
          "name": "Acme Corp",
          "slug": "acme"
        }
      }
    ]
    """

  const val LOGIN_OPTIONS =
    """
    [
      {
        "tenant": {
          "id": "$TENANT_ID",
          "name": "Acme Corp",
          "slug": "acme"
        },
        "loginMethod": {
          "id": "$LOGIN_METHOD_ID",
          "code": "password",
          "kind": "PASSWORD",
          "name": "Password"
        }
      }
    ]
    """

  const val ISSUED_TOKEN =
    """
    {
      "id": "$BEARER_TOKEN_ID",
      "token": "wbk_live_01JABCDEFGHJKMNPQRSTVWXYZ0",
      "expiresAt": "2027-07-02T12:00:00+00:00"
    }
    """

  const val FEDERATED_AUTHORIZE =
    """
    {
      "authorizationUrl": "https://login.example.com/oauth2/authorize?client_id=workbench&state=01JABCDEFGH",
      "state": "01JABCDEFGH"
    }
    """

  const val MAGIC_LINK_REQUEST =
    """
    {
      "email": "user@example.com",
      "tenantId": "$TENANT_ID",
      "loginMethodId": "$LOGIN_METHOD_ID"
    }
    """

  const val ROLE_CREATED =
    """
    {
      "id": "$ROLE_ID",
      "scope": "TENANT",
      "code": "admin",
      "name": "Administrator",
      "description": "Full access",
      "builtin": false
    }
    """

  const val ROLE_LIST =
    """
    [
      {
        "id": "$ROLE_ID",
        "scope": "TENANT",
        "code": "admin",
        "name": "Administrator",
        "description": "Full access",
        "builtin": true
      }
    ]
    """

  const val ACTION_CREATED =
    """
    {
      "code": "project.create",
      "description": "Create projects in the tenant."
    }
    """

  const val ACTION_LIST =
    """
    [
      {
        "code": "project.create",
        "description": "Create projects in the tenant."
      }
    ]
    """

  const val POLICY_CREATED =
    """
    {
      "id": "$POLICY_ID",
      "roleId": "$ROLE_ID",
      "action": "project.create",
      "effect": "ALLOW",
      "resourcePattern": "project",
      "validFrom": "2026-07-02T10:00:00+00:00",
      "validTo": null
    }
    """

  const val POLICY_LIST =
    """
    [
      {
        "id": "$POLICY_ID",
        "roleId": "$ROLE_ID",
        "action": "project.create",
        "effect": "ALLOW",
        "resourcePattern": "project",
        "validFrom": "2026-07-02T10:00:00+00:00",
        "validTo": null
      }
    ]
    """

  const val ROLE_ASSIGNMENT_CREATED =
    """
    {
      "id": "$ASSIGNMENT_ID",
      "userId": "$USER_ID",
      "roleId": "$ROLE_ID",
      "projectId": "$PROJECT_ID",
      "validFrom": "2026-07-02T10:00:00+00:00",
      "validTo": null
    }
    """

  const val ROLE_ASSIGNMENT_LIST =
    """
    [
      {
        "id": "$ASSIGNMENT_ID",
        "userId": "$USER_ID",
        "roleId": "$ROLE_ID",
        "projectId": "$PROJECT_ID",
        "validFrom": "2026-07-02T10:00:00+00:00",
        "validTo": null
      }
    ]
    """

  const val CREATE_PROJECT_REQUEST =
    """
    {
      "identifier": "CORE",
      "name": "Core Platform",
      "description": "Platform engineering."
    }
    """

  const val CREATE_PROJECT_REQUEST_INVALID =
    """
    {
      "identifier": "invalid",
      "name": "Core Platform",
      "description": "Platform engineering."
    }
    """

  const val ASSIGN_ROLE_REQUEST =
    """
    {
      "userId": "$USER_ID",
      "roleId": "$ROLE_ID",
      "projectId": "$PROJECT_ID"
    }
    """

  const val CREATE_POLICY_REQUEST =
    """
    {
      "roleId": "$ROLE_ID",
      "action": "project.create",
      "effect": "ALLOW",
      "resourcePattern": "project"
    }
    """

  const val CREATE_ROLE_REQUEST =
    """
    {
      "scope": "TENANT",
      "code": "admin",
      "name": "Administrator",
      "description": "Full access"
    }
    """
}
