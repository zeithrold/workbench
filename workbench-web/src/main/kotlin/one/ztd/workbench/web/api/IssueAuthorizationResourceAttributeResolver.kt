package one.ztd.workbench.web.api

import one.ztd.workbench.agile.project.ProjectRepository
import one.ztd.workbench.agile.workitem.WorkItemRepository
import one.ztd.workbench.identity.permission.AuthorizationResourceAttributeResolver
import one.ztd.workbench.identity.permission.model.AuthorizationRequest
import one.ztd.workbench.identity.permission.model.AuthorizationResource
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class IssueAuthorizationResourceAttributeResolver(
  private val workItems: WorkItemRepository,
  private val projects: ProjectRepository,
) : AuthorizationResourceAttributeResolver {
  private val logger = LoggerFactory.getLogger(javaClass)

  override suspend fun supports(resource: AuthorizationResource): Boolean =
    resource.type == "issue" && resource.id != null

  override suspend fun resolveAttributes(request: AuthorizationRequest): Map<String, String> {
    val resource = request.resource
    val tenantId = request.tenantId
    val projectId = resource.projectId
    val issueId = resource.id
    if (tenantId == null || projectId == null || issueId == null) return emptyMap()
    val workItem = workItems.findByApiId(tenantId, projectId, issueId)
    if (workItem == null) {
      logger.warn(
        "authorization_issue_not_found tenantId={} projectId={} issueId={}",
        tenantId,
        projectId,
        issueId,
      )
      return emptyMap()
    }
    val projectApiId =
      projects.findById(tenantId, projectId)?.apiId?.value
        ?: workItems.resolveProjectApiId(tenantId, projectId)?.value
        ?: return emptyMap()
    return buildMap {
      put("reporter", workItem.reporterApiId.value)
      workItem.assigneeApiId?.let { put("assignee", it.value) }
      put("status", workItem.statusApiId.value)
      put("statusGroup", workItem.statusGroup.dbValue)
      put("issueType", workItem.issueTypeApiId.value)
      put("issueTypeConfig", workItem.issueTypeConfigApiId.value)
      put("project", projectApiId)
    }
  }
}
