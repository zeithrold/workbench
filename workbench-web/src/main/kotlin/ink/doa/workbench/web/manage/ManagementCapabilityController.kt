package ink.doa.workbench.web.manage

import ink.doa.workbench.application.permission.ManagementCapabilityService
import ink.doa.workbench.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.kernel.common.context.InstanceContextSummary
import ink.doa.workbench.tenant.common.summary.TenantSummary
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.AuthenticatedOnly
import ink.doa.workbench.web.api.InstanceScoped
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import ink.doa.workbench.web.api.context.InstanceRequestContext
import ink.doa.workbench.web.api.context.TenantRequestContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class InstanceCapabilityResponse(
  val scope: String = "INSTANCE",
  val instance: InstanceContextSummary,
  val actions: List<String>,
)

data class TenantCapabilityResponse(
  val scope: String = "TENANT",
  val tenant: TenantSummary,
  val actions: List<String>,
)

@RestController
@Authenticated
@AuthenticatedOnly
@SessionSecured
@StandardErrorResponses
@Tag(name = "Management Capabilities", description = "Effective management capabilities.")
class ManagementCapabilityController(private val capabilities: ManagementCapabilityService) {
  @GetMapping("/api/admin/capabilities")
  @InstanceScoped
  @Operation(
    summary = "Get effective instance management capabilities",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Effective capabilities",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun instance(
    principal: AuthenticatedPrincipal,
    instanceContext: InstanceRequestContext,
  ): InstanceCapabilityResponse =
    InstanceCapabilityResponse(
      instance = instanceContext.instance,
      actions = capabilities.instanceCapabilities(principal),
    )

  @GetMapping("/api/manage/capabilities")
  @TenantScoped
  @Operation(
    summary = "Get effective tenant management capabilities",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Effective capabilities",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun tenant(
    principal: AuthenticatedPrincipal,
    tenantContext: TenantRequestContext,
  ): TenantCapabilityResponse =
    TenantCapabilityResponse(
      tenant =
        TenantSummary(
          id = tenantContext.tenant.publicId,
          name = tenantContext.tenant.name,
          slug = tenantContext.tenant.slug,
        ),
      actions = capabilities.tenantCapabilities(principal, tenantContext.tenant.id),
    )
}
