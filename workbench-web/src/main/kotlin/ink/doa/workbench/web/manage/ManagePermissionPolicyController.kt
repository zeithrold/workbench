package ink.doa.workbench.web.manage

import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.application.permission.AddPolicyRuleCommand
import ink.doa.workbench.application.permission.PermissionPolicyManagementService
import ink.doa.workbench.identity.permission.model.PermissionEffect
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import ink.doa.workbench.web.api.context.TenantRequestContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
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
  @Operation(summary = "List permission policies")
  suspend fun listPolicies(tenantContext: TenantRequestContext): List<PermissionPolicyResponse> =
    permissionPolicyManagementService.listPolicies(tenantContext.tenant.id).map {
      PermissionPolicyResponse.from(it)
    }

  @GetMapping("/permission-policies/{id}")
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(summary = "Get permission policy")
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
      permissionPolicyManagementService.createPolicy(
        tenantId = tenantContext.tenant.id,
        code = request.code,
        name = request.name,
        description = request.description,
      )
    )

  @PatchMapping("/permission-policies/{id}")
  @Authorize(action = "permission.policy.manage", resource = "permission")
  @Operation(summary = "Update custom permission policy")
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
}
