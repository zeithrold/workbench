package ink.doa.workbench.web.api.warning

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import ink.doa.workbench.agile.project.ProjectSummary
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
  description = "Structured warning envelope returned in the X-Workbench-Warning response header."
)
data class WorkbenchWarningEnvelope(
  val version: Int,
  val items: List<WorkbenchWarningItem>,
)

@Schema(description = "Single non-blocking business risk warning.")
data class WorkbenchWarningItem(
  val code: String,
  val severity: String,
  val message: String,
  val meta: WorkbenchWarningMetaSchema,
)

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.EXISTING_PROPERTY,
  property = "kind",
)
@JsonSubTypes(
  JsonSubTypes.Type(
    value = ProjectDestroyScheduledMetaSchema::class,
    name = "projectDestroyScheduled",
  ),
  JsonSubTypes.Type(value = WarningTruncatedMetaSchema::class, name = "warningTruncated"),
)
@Schema(
  description = "Typed warning metadata.",
  oneOf = [ProjectDestroyScheduledMetaSchema::class, WarningTruncatedMetaSchema::class],
  discriminatorProperty = "kind",
)
sealed interface WorkbenchWarningMetaSchema {
  val kind: String
}

@Schema(description = "Project destruction has been scheduled.")
data class ProjectDestroyScheduledMetaSchema(
  override val kind: String = "projectDestroyScheduled",
  val project: ProjectSummary,
  val deleteReason: String? = null,
) : WorkbenchWarningMetaSchema

@Schema(description = "Additional warnings were omitted from the response header.")
data class WarningTruncatedMetaSchema(override val kind: String = "warningTruncated") :
  WorkbenchWarningMetaSchema
