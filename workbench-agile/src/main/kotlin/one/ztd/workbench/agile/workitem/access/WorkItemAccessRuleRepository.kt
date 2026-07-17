package one.ztd.workbench.agile.workitem.access

import java.util.UUID

interface WorkItemAccessRuleRepository {
  suspend fun create(command: CreateWorkItemAccessRuleCommand): WorkItemAccessRuleRecord

  suspend fun listByConfig(tenantId: UUID, issueTypeConfigId: UUID): List<WorkItemAccessRuleRecord>

  suspend fun findByApiId(tenantId: UUID, apiId: String): WorkItemAccessRuleRecord?

  suspend fun deactivate(tenantId: UUID, ruleId: UUID): Boolean
}
