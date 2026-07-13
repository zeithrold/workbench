package ink.doa.workbench.web.api.warning

import ink.doa.workbench.agile.project.ProjectSummary
import ink.doa.workbench.kernel.common.warning.WorkbenchWarning
import ink.doa.workbench.kernel.common.warning.WorkbenchWarningCode
import ink.doa.workbench.kernel.common.warning.WorkbenchWarningConstants
import ink.doa.workbench.kernel.common.warning.meta.ProjectDestroyScheduledMeta
import ink.doa.workbench.kernel.common.warning.meta.WarningTruncatedMeta
import ink.doa.workbench.kernel.common.warning.meta.WorkbenchWarningMeta
import tools.jackson.module.kotlin.jacksonObjectMapper

class WorkbenchWarningSupport {
  private val objectMapper = jacksonObjectMapper()

  fun prepareForHeader(warnings: List<WorkbenchWarning>): List<WorkbenchWarning> {
    if (warnings.isEmpty()) return emptyList()
    val limited = warnings.take(WorkbenchWarningConstants.MaxItems).toMutableList()
    if (warnings.size > WorkbenchWarningConstants.MaxItems) {
      appendTruncatedMarker(limited)
    }
    return fitHeaderBudget(limited)
  }

  fun toHeaderValue(warnings: List<WorkbenchWarning>): String =
    encodeEnvelope(prepareForHeader(warnings))

  private fun fitHeaderBudget(warnings: List<WorkbenchWarning>): List<WorkbenchWarning> {
    var candidate = warnings.toList()
    while (candidate.isNotEmpty()) {
      if (
        encodeEnvelope(candidate).toByteArray(Charsets.UTF_8).size <=
          WorkbenchWarningConstants.MaxHeaderBytes
      ) {
        return candidate
      }
      candidate =
        candidate.toMutableList().apply {
          if (lastOrNull()?.code == WorkbenchWarningCode.WARNING_TRUNCATED) {
            removeAt(lastIndex)
          }
          if (isNotEmpty()) {
            removeAt(lastIndex)
          }
          if (none { it.code == WorkbenchWarningCode.WARNING_TRUNCATED }) {
            appendTruncatedMarker(this)
          }
        }
    }
    return listOf(truncatedWarning())
  }

  private fun appendTruncatedMarker(warnings: MutableList<WorkbenchWarning>) {
    if (warnings.none { it.code == WorkbenchWarningCode.WARNING_TRUNCATED }) {
      warnings += truncatedWarning()
    }
  }

  private fun truncatedWarning(): WorkbenchWarning =
    WorkbenchWarning(
      code = WorkbenchWarningCode.WARNING_TRUNCATED,
      message = WorkbenchWarningCode.WARNING_TRUNCATED.defaultMessage,
      severity = WorkbenchWarningCode.WARNING_TRUNCATED.defaultSeverity,
      meta = WarningTruncatedMeta,
    )

  private fun encodeEnvelope(warnings: List<WorkbenchWarning>): String {
    val envelope =
      WorkbenchWarningEnvelope(
        version = WorkbenchWarningConstants.EnvelopeVersion,
        items = warnings.map(::toItem),
      )
    return objectMapper.writeValueAsString(envelope)
  }

  private fun toItem(warning: WorkbenchWarning): WorkbenchWarningItem =
    WorkbenchWarningItem(
      code = warning.code.code,
      severity = warning.severity.jsonValue,
      message = warning.message,
      meta = toMetaSchema(warning.meta),
    )

  private fun toMetaSchema(meta: WorkbenchWarningMeta): WorkbenchWarningMetaSchema =
    when (meta) {
      is ProjectDestroyScheduledMeta ->
        ProjectDestroyScheduledMetaSchema(
          project =
            ProjectSummary(
              id = meta.project.id,
              identifier = meta.project.identifier,
              name = meta.project.name,
            ),
          deleteReason = meta.deleteReason,
        )
      is WarningTruncatedMeta -> WarningTruncatedMetaSchema()
      else -> error("Unsupported warning meta for serialization: ${meta::class.simpleName}")
    }
}
