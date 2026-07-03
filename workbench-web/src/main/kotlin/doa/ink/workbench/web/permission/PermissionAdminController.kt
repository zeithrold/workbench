package doa.ink.workbench.web.permission

import doa.ink.workbench.core.common.context.RequestContext
import doa.ink.workbench.core.common.context.TenantRequestContext
import doa.ink.workbench.service.permission.PermissionManagementService
import doa.ink.workbench.web.api.Authenticated
import doa.ink.workbench.web.api.Authorize
import doa.ink.workbench.web.api.OpenApiExamples
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import doa.ink.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/permissions")
@Authenticated
@TenantScoped
@Tag(
  name = "Permission Admin",
  description =
    "Tenant permission administration under /api/admin. Requires session auth, active tenant, and specific manage permissions.",
)
@SessionSecured
@StandardErrorResponses
@Suppress("UnusedParameter")
class PermissionAdminController(private val service: PermissionManagementService) {
  @GetMapping("/roles")
  @Authorize(action = "permission.role.manage", resource = "permission")
  @Operation(
    summary = "List roles",
    description = "Lists roles defined for the active session tenant.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Tenant roles",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = RoleResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.ROLE_LIST,
                    )
                  ],
              )
            ],
        )
      ],
  )
  suspend fun listRoles(tenantContext: TenantRequestContext): List<RoleResponse> =
    service.listRoles(tenantContext.tenantId).map { RoleResponse.from(it) }

  @PostMapping("/roles")
  @Authorize(action = "permission.role.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create role",
    description = "Creates a custom role in the active session tenant.",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Role created",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = RoleResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "created",
                      value = OpenApiExamples.ROLE_CREATED,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "400",
          description = "Validation failed",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "validationFailed",
                      value = OpenApiExamples.VALIDATION_FAILED,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "403",
          description = "Permission denied",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "permissionDenied",
                      value = OpenApiExamples.PERMISSION_DENIED,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun createRole(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = CreateRoleRequest::class),
            examples =
              [
                ExampleObject(
                  name = "valid",
                  value = OpenApiExamples.CREATE_ROLE_REQUEST,
                )
              ],
          )
        ]
    )
    @Valid
    @RequestBody
    request: CreateRoleRequest,
    tenantContext: TenantRequestContext,
  ): RoleResponse =
    RoleResponse.from(
      service.createRole(
        tenantId = tenantContext.tenantId,
        scope = request.scope,
        code = request.code,
        name = request.name,
        description = request.description,
      )
    )

  @GetMapping("/actions")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(
    summary = "List permission actions",
    description = "Lists registered permission action codes available for policies.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Permission actions",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = ActionResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.ACTION_LIST,
                    )
                  ],
              )
            ],
        )
      ],
  )
  suspend fun listActions(tenantContext: TenantRequestContext): List<ActionResponse> =
    service.listActions().map { ActionResponse.from(it) }

  @PostMapping("/actions")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Ensure permission action",
    description =
      "Upserts a global permission action registry entry. Idempotent: returns the existing action when the code is already registered.",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Action ensured",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = ActionResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "created",
                      value = OpenApiExamples.ACTION_CREATED,
                    )
                  ],
              )
            ],
        )
      ],
  )
  suspend fun ensureAction(
    @Valid @RequestBody request: EnsureActionRequest,
    tenantContext: TenantRequestContext,
  ): ActionResponse = ActionResponse.from(service.ensureAction(request.code, request.description))

  @GetMapping("/policies")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(
    summary = "List policies",
    description = "Lists active and historical policies for the active session tenant.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Tenant policies",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = PolicyResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.POLICY_LIST,
                    )
                  ],
              )
            ],
        )
      ],
  )
  suspend fun listPolicies(tenantContext: TenantRequestContext): List<PolicyResponse> =
    service.listPolicies(tenantContext.tenantId).map { PolicyResponse.from(it) }

  @PostMapping("/policies")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create policy",
    description =
      "Creates an allow or deny policy binding a role to an action on a resource pattern. Response uses bare roleId FK without role embed.",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Policy created",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = PolicyResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "created",
                      value = OpenApiExamples.POLICY_CREATED,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "400",
          description = "Validation failed",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "validationFailed",
                      value = OpenApiExamples.VALIDATION_FAILED,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun createPolicy(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = CreatePolicyRequest::class),
            examples =
              [
                ExampleObject(
                  name = "valid",
                  value = OpenApiExamples.CREATE_POLICY_REQUEST,
                )
              ],
          )
        ]
    )
    @Valid
    @RequestBody
    request: CreatePolicyRequest,
    tenantContext: TenantRequestContext,
    requestContext: RequestContext,
  ): PolicyResponse =
    PolicyResponse.from(
      service.createPolicy(
        tenantId = tenantContext.tenantId,
        rolePublicId = request.roleId,
        actionCode = request.action,
        effect = request.effect,
        resourcePattern = request.resourcePattern,
        condition = request.condition?.toInput()?.toModel(),
        actorUserId = requestContext.actorUserId,
      )
    )

  @DeleteMapping("/policies/{id}")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Expire policy",
    description = "Soft-expires a policy by public id. Returns 204 with an empty body.",
    responses =
      [
        ApiResponse(responseCode = "204", description = "Policy expired"),
        ApiResponse(
          responseCode = "404",
          description = "Policy not found",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "notFound",
                      value = OpenApiExamples.RESOURCE_NOT_FOUND,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun expirePolicy(
    @Parameter(description = "Public policy id.", example = OpenApiExamples.POLICY_ID)
    @PathVariable
    id: String,
    tenantContext: TenantRequestContext,
  ) {
    service.expirePolicy(tenantContext.tenantId, id)
  }

  @GetMapping("/assignments")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @Operation(
    summary = "List role assignments",
    description = "Lists role assignments in the active session tenant.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Role assignments",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = RoleAssignmentResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "success",
                      value = OpenApiExamples.ROLE_ASSIGNMENT_LIST,
                    )
                  ],
              )
            ],
        )
      ],
  )
  suspend fun listAssignments(tenantContext: TenantRequestContext): List<RoleAssignmentResponse> =
    service.listAssignments(tenantContext.tenantId).map { RoleAssignmentResponse.from(it) }

  @PostMapping("/assignments")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Assign role",
    description =
      "Assigns a role to a user, optionally scoped to a project. Request uses flat entity id refs; response uses bare FK fields.",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Assignment created",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = RoleAssignmentResponse::class),
                examples =
                  [
                    ExampleObject(
                      name = "created",
                      value = OpenApiExamples.ROLE_ASSIGNMENT_CREATED,
                    )
                  ],
              )
            ],
        ),
        ApiResponse(
          responseCode = "400",
          description = "Validation failed",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "validationFailed",
                      value = OpenApiExamples.VALIDATION_FAILED,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun assignRole(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = AssignRoleRequest::class),
            examples =
              [
                ExampleObject(
                  name = "valid",
                  value = OpenApiExamples.ASSIGN_ROLE_REQUEST,
                )
              ],
          )
        ]
    )
    @Valid
    @RequestBody
    request: AssignRoleRequest,
    tenantContext: TenantRequestContext,
    requestContext: RequestContext,
  ): RoleAssignmentResponse =
    RoleAssignmentResponse.from(
      service.assignRole(
        tenantId = tenantContext.tenantId,
        userPublicId = request.userId,
        rolePublicId = request.roleId,
        projectPublicId = request.projectId,
        actorUserId = requestContext.actorUserId,
      )
    )

  @DeleteMapping("/assignments/{id}")
  @Authorize(action = "permission.assignment.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Revoke role assignment",
    description = "Soft-revokes a role assignment by public id. Returns 204 with an empty body.",
    responses =
      [
        ApiResponse(responseCode = "204", description = "Assignment revoked"),
        ApiResponse(
          responseCode = "404",
          description = "Assignment not found",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
                examples =
                  [
                    ExampleObject(
                      name = "notFound",
                      value = OpenApiExamples.RESOURCE_NOT_FOUND,
                    )
                  ],
              )
            ],
        ),
      ],
  )
  suspend fun revokeAssignment(
    @Parameter(description = "Public assignment id.", example = OpenApiExamples.ASSIGNMENT_ID)
    @PathVariable
    id: String,
    tenantContext: TenantRequestContext,
  ) {
    service.revokeAssignment(tenantContext.tenantId, id)
  }
}
