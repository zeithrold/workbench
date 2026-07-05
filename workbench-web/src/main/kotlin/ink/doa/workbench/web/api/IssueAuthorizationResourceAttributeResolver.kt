package ink.doa.workbench.web.api

import ink.doa.workbench.core.permission.AuthorizationResourceAttributeResolver
import ink.doa.workbench.core.permission.model.AuthorizationRequest
import ink.doa.workbench.core.permission.model.AuthorizationResource
import ink.doa.workbench.core.workitem.WorkItemRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class IssueAuthorizationResourceAttributeResolver(
  private val workItems: WorkItemRepository,
) : AuthorizationResourceAttributeResolver {
  private val logger = LoggerFactory.getLogger(javaClass)

  override suspend fun supports(resource: AuthorizationResource): Boolean =
    resource.type == "issue" && resource.id != null

  override suspend fun resolveAttributes(request: AuthorizationRequest): Map<String, String> {
    val resource = request.resource
    val tenantId = request.tenantId ?: return emptyMap()
    val projectId = resource.projectId ?: return emptyMap()
    val issueId = resource.id ?: return emptyMap()
    val workItem =
      workItems.findByApiId(tenantId, projectId, issueId)
        ?: run {
          logger.warn(
            "authorization_issue_not_found tenantId={} projectId={} issueId={}",
            tenantId,
            projectId,
            issueId,
          )
          return emptyMap()
        }
    return buildMap {
      put("reporter", workItem.reporterId.toString())
      workItem.assigneeId?.let { put("assignee", it.toString()) }
      put("status", workItem.statusApiId.value)
      put("statusGroup", workItem.statusGroup.dbValue)
      put("issueType", workItem.issueTypeApiId.value)
      put("issueTypeConfig", workItem.issueTypeConfigApiId.value)
      put("project", workItem.projectId.toString())
    }
  }
}
