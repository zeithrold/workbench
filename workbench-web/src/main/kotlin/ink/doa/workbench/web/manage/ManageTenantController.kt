package ink.doa.workbench.web.manage

import ink.doa.workbench.application.instance.TenantManagementApplicationService
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import ink.doa.workbench.web.api.context.TenantRequestContext
import ink.doa.workbench.web.instance.PatchTenantRequest
import ink.doa.workbench.web.instance.TenantResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/manage/tenant")
@Authenticated
@TenantScoped
@SessionSecured
@StandardErrorResponses
@Tag(name = "Tenant Management", description = "Current-tenant settings management.")
class ManageTenantController(private val tenants: TenantManagementApplicationService) {
  @GetMapping
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(
    summary = "Get current tenant settings",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Tenant settings",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun get(tenantContext: TenantRequestContext): TenantResponse =
    TenantResponse.from(tenants.get(tenantContext.tenant.publicId.value))

  @PatchMapping
  @Authorize(action = "tenant.update", resource = "tenant")
  @Operation(
    summary = "Update current tenant settings",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Updated tenant settings",
          useReturnTypeSchema = true,
        )
      ],
  )
  suspend fun patch(
    @Valid @RequestBody request: PatchTenantRequest,
    tenantContext: TenantRequestContext,
  ): TenantResponse =
    TenantResponse.from(
      tenants.update(
        tenantPublicId = tenantContext.tenant.publicId.value,
        name = request.name,
        slug = request.slug,
        timezone = request.timezone,
        locale = request.locale,
      )
    )
}
