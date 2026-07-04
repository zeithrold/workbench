package ink.doa.workbench.web.workitem

import ink.doa.workbench.agile.workitem.IssueTypeConfigService
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.ProjectScoped
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects/{id}/work-item/type-configs")
@Tag(name = "Project Work Item Configuration")
@SessionSecured
@StandardErrorResponses
class ProjectWorkItemConfigurationController(private val configs: IssueTypeConfigService) {
  @GetMapping("/effective/{issueTypeId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "workitem.config.read", resource = "workitem.config")
  @Operation(summary = "Resolve effective work item type config")
  suspend fun effective(
    @PathVariable issueTypeId: String,
    projectContext: ProjectRequestContext,
  ): EffectiveIssueTypeConfigResponse =
    EffectiveIssueTypeConfigResponse.from(
      configs.resolveEffective(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        issueTypeApiIdOrCode = issueTypeId,
      )
    )
}
