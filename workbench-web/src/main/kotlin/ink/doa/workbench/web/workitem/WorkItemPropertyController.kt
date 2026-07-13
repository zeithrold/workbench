package ink.doa.workbench.web.workitem

import com.fasterxml.jackson.databind.ObjectMapper
import ink.doa.workbench.agile.workitem.WorkItemCatalogService
import ink.doa.workbench.agile.workitem.model.CreatePropertyDefinitionCommand
import ink.doa.workbench.agile.workitem.model.WorkItemPropertyDataType
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/work-item-catalog/properties")
@Tag(name = "Work Item Configuration", description = "Tenant-scoped work item catalog management.")
@SessionSecured
@StandardErrorResponses
class WorkItemPropertyController(private val catalog: WorkItemCatalogService) {
  private val objectMapper = ObjectMapper()

  @GetMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.read", resource = "workitem.config")
  @Operation(summary = "List work item properties")
  suspend fun list(tenantContext: TenantRequestContext): List<PropertyDefinitionResponse> =
    catalog.listProperties(tenantContext.tenant.id).map(PropertyDefinitionResponse::from)

  @PostMapping
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.manage", resource = "workitem.config")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create work item property")
  suspend fun create(
    @Valid @RequestBody request: CreatePropertyDefinitionRequest,
    tenantContext: TenantRequestContext,
  ): PropertyDefinitionResponse =
    PropertyDefinitionResponse.from(
      catalog.createProperty(
        CreatePropertyDefinitionCommand(
          tenantId = tenantContext.tenant.id,
          code = request.code,
          name = request.name,
          description = request.description,
          dataType = WorkItemPropertyDataType.fromDbValue(request.dataType),
          isArray = request.isArray ?: false,
          validationSchema = request.validationSchema.toJsonObject(objectMapper),
          searchConfig = request.searchConfig.toJsonObject(objectMapper),
        )
      )
    )

  @PatchMapping("/{propertyId}/deactivate")
  @Authenticated
  @TenantScoped
  @Authorize(action = "workitem.config.manage", resource = "workitem.config")
  @Operation(summary = "Deactivate work item property")
  suspend fun deactivate(
    @PathVariable propertyId: String,
    tenantContext: TenantRequestContext,
  ): PropertyDefinitionResponse =
    PropertyDefinitionResponse.from(
      catalog.deactivateProperty(
        tenantContext.tenant.id,
        propertyId,
        actorUserId(tenantContext),
      )
    )
}
