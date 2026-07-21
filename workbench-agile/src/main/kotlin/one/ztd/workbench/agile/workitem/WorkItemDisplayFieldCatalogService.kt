package one.ztd.workbench.agile.workitem

import java.util.UUID
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Service

data class WorkItemDisplayFieldDefinition(
  val key: String,
  val name: String,
  val dataType: String,
  val array: Boolean = false,
  val propertyId: String? = null,
  val validation: JsonObject = JsonObject(emptyMap()),
)

@Service
class WorkItemDisplayFieldCatalogService(
  private val configs: IssueTypeConfigRepository,
  private val properties: PropertyDefinitionRepository,
) {
  suspend fun list(tenantId: UUID, projectId: UUID): List<WorkItemDisplayFieldDefinition> {
    val definitions =
      properties.listProperties(tenantId).filter { it.isActive }.associateBy { it.id }
    val custom =
      configs
        .listConfigs(tenantId, projectId)
        .asSequence()
        .filter { it.config.isActive }
        .flatMap { it.properties.asSequence() }
        .distinctBy { it.code }
        .mapNotNull { configured ->
          val definition = definitions[configured.propertyId] ?: return@mapNotNull null
          WorkItemDisplayFieldDefinition(
            key = "property.${configured.code}",
            name = configured.name,
            dataType = configured.dataType.dbValue,
            array = definition.isArray,
            propertyId = configured.propertyApiId.value,
            validation =
              JsonObject(
                definition.validationSchema.toMap() + configured.validationOverride.toMap()
              ),
          )
        }
        .sortedBy { it.name.lowercase() }
        .toList()
    return SYSTEM_FIELDS + custom
  }

  private companion object {
    val SYSTEM_FIELDS =
      listOf(
        WorkItemDisplayFieldDefinition("key", "Key", "text"),
        WorkItemDisplayFieldDefinition("title", "Title", "text"),
        WorkItemDisplayFieldDefinition("issueType", "Type", "issue_type"),
        WorkItemDisplayFieldDefinition("status", "Status", "status"),
        WorkItemDisplayFieldDefinition("priority", "Priority", "priority"),
        WorkItemDisplayFieldDefinition("assignee", "Assignee", "user"),
        WorkItemDisplayFieldDefinition("sprint", "Sprint", "sprint"),
        WorkItemDisplayFieldDefinition("updatedAt", "Updated", "datetime"),
      )
  }
}
