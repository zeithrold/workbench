package one.ztd.workbench.web.workitem

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import one.ztd.workbench.agile.workitem.IssueTypeConfigService
import one.ztd.workbench.web.api.Authenticated
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.ProjectScoped
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.TenantScoped
import one.ztd.workbench.web.api.context.ProjectRequestContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects/{id}/work-item-types")
@Tag(name = "Project Work Item Configuration")
@SessionSecured
@StandardErrorResponses
class ProjectWorkItemConfigurationController(private val configs: IssueTypeConfigService) {
  @GetMapping("/{issueTypeId}/effective-config")
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
