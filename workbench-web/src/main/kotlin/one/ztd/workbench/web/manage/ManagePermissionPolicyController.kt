package one.ztd.workbench.web.manage

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import one.ztd.workbench.application.permission.AddPolicyRuleCommand
import one.ztd.workbench.application.permission.CreatePermissionPolicyDocumentCommand
import one.ztd.workbench.application.permission.PermissionPolicyDocumentRuleCommand
import one.ztd.workbench.application.permission.PermissionPolicyManagementService
import one.ztd.workbench.application.permission.ReplacePermissionPolicyDocumentCommand
import one.ztd.workbench.identity.permission.model.PermissionEffect
import one.ztd.workbench.web.api.Authenticated
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.TenantScoped
import one.ztd.workbench.web.api.context.TenantRequestContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/manage")
@Authenticated
@TenantScoped
@SessionSecured
@StandardErrorResponses
@Tag(name = "Tenant Management", description = "Current tenant foreground management APIs.")
class ManagePermissionPolicyController(
  private val permissionPolicyManagementService: PermissionPolicyManagementService
) {
  private val objectMapper = ObjectMapper()

  @GetMapping("/permission-policies")
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(
    summary = "List permission policies",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Tenant policies",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun listPolicies(tenantContext: TenantRequestContext): List<PermissionPolicyResponse> =
    permissionPolicyManagementService.listPolicies(tenantContext.tenant.id).map {
      PermissionPolicyResponse.from(it)
    }

  @GetMapping("/permission-policies/{id}")
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(
    summary = "Get permission policy",
    responses =
      [
        ApiResponse(responseCode = "200", description = "Tenant policy", useReturnTypeSchema = true)
      ],
  )
  suspend fun getPolicy(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ): PermissionPolicyResponse =
    PermissionPolicyResponse.from(
      permissionPolicyManagementService.getPolicy(tenantContext.tenant.id, id)
    )

  @PostMapping("/permission-policies")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create custom permission policy")
  suspend fun createPolicy(
    @Valid @RequestBody request: CreatePermissionPolicyRequest,
    tenantContext: TenantRequestContext,
  ): PermissionPolicyResponse =
    PermissionPolicyResponse.from(
      permissionPolicyManagementService.createDocument(
        CreatePermissionPolicyDocumentCommand(
          tenantId = tenantContext.tenant.id,
          schemaVersion = request.schemaVersion,
          code = request.code,
          name = request.name,
          description = request.description,
          rules = request.rules.map(::toRuleCommand),
        )
      )
    )

  @PostMapping("/permission-policies/simulate")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(
    summary = "Simulate a tenant permission policy draft",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Draft decision",
          useReturnTypeSchema = true,
        )
      ],
  )
  fun simulatePolicy(
    @Valid @RequestBody request: SimulateTenantPermissionPolicyRequest,
    tenantContext: TenantRequestContext,
  ): TenantPolicySimulationResponse =
    TenantPolicySimulationResponse.from(
      permissionPolicyManagementService.simulate(
        tenantContext.tenant.id,
        request.schemaVersion,
        request.rules.map(::toRuleCommand),
        request.action,
      )
    )

  @PutMapping("/permission-policies/{id}")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(
    summary = "Replace custom permission policy document",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Updated tenant policy",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun replacePolicy(
    @PathVariable id: String,
    @Valid @RequestBody request: ReplacePermissionPolicyRequest,
    tenantContext: TenantRequestContext,
  ): PermissionPolicyResponse =
    PermissionPolicyResponse.from(
      permissionPolicyManagementService.replaceDocument(
        ReplacePermissionPolicyDocumentCommand(
          tenantId = tenantContext.tenant.id,
          policyPublicId = id,
          schemaVersion = request.schemaVersion,
          revision = request.revision,
          code = request.code,
          name = request.name,
          description = request.description,
          rules = request.rules.map(::toRuleCommand),
        )
      )
    )

  @PatchMapping("/permission-policies/{id}")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(
    summary = "Update custom permission policy",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Updated tenant policy",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun updatePolicy(
    @PathVariable id: String,
    @Valid @RequestBody request: UpdatePermissionPolicyRequest,
    tenantContext: TenantRequestContext,
  ): PermissionPolicyResponse =
    PermissionPolicyResponse.from(
      permissionPolicyManagementService.updatePolicy(
        tenantId = tenantContext.tenant.id,
        publicId = id,
        name = request.name,
        description = request.description,
      )
    )

  @DeleteMapping("/permission-policies/{id}")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete custom permission policy")
  suspend fun deletePolicy(
    @PathVariable id: String,
    tenantContext: TenantRequestContext,
  ) {
    permissionPolicyManagementService.deletePolicy(tenantContext.tenant.id, id)
  }

  @PostMapping("/permission-policies/{id}/rules")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add rule to custom permission policy")
  suspend fun addPolicyRule(
    @PathVariable id: String,
    @Valid @RequestBody request: CreatePermissionPolicyRuleRequest,
    tenantContext: TenantRequestContext,
  ): PermissionPolicyResponse =
    PermissionPolicyResponse.from(
      permissionPolicyManagementService.addPolicyRule(
        AddPolicyRuleCommand(
          tenantId = tenantContext.tenant.id,
          policyPublicId = id,
          action = request.action,
          resourcePattern = request.resourcePattern,
          effect =
            request.effect?.let { PermissionEffect.valueOf(it.uppercase()) }
              ?: PermissionEffect.ALLOW,
          conditionJson = request.condition?.let { objectMapper.writeValueAsString(it) },
        )
      )
    )

  private fun toRuleCommand(request: PermissionPolicyRuleRequest) =
    PermissionPolicyDocumentRuleCommand(
      id = request.id,
      action = request.action,
      resourcePattern = request.resourcePattern,
      effect = PermissionEffect.valueOf(request.effect.uppercase()),
      conditionJson = request.condition?.let { objectMapper.writeValueAsString(it) },
    )
}
