package ink.doa.workbench.core.common.warning

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.summary.ProjectSummary
import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.common.warning.meta.ApiVersionDeprecatedMeta
import ink.doa.workbench.core.common.warning.meta.ProjectDestroyScheduledMeta
import ink.doa.workbench.core.common.warning.meta.TenantDestroyScheduledMeta
import ink.doa.workbench.core.common.warning.meta.WarningTruncatedMeta
import ink.doa.workbench.core.common.warning.meta.WorkbenchWarningMeta

object WorkbenchWarningMetaValidator {
  private val ApiVersionPattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")
  private val IsoDatePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

  fun validate(code: WorkbenchWarningCode, meta: WorkbenchWarningMeta) {
    require(meta.kind == expectedKind(code.metaType)) {
      "Warning meta kind '${meta.kind}' does not match code '${code.code}'."
    }
    require(meta::class == code.metaType || meta is WarningTruncatedMeta) {
      "Warning meta type '${meta::class.simpleName}' does not match code '${code.code}'."
    }
    when (meta) {
      is ProjectDestroyScheduledMeta -> validateProjectEmbed(meta.project)
      is TenantDestroyScheduledMeta -> validateTenantEmbed(meta.tenant)
      is ApiVersionDeprecatedMeta -> validateApiVersionDeprecated(meta)
      is WarningTruncatedMeta -> Unit
    }
  }

  private fun expectedKind(metaType: kotlin.reflect.KClass<out WorkbenchWarningMeta>): String =
    when (metaType) {
      ProjectDestroyScheduledMeta::class -> "projectDestroyScheduled"
      TenantDestroyScheduledMeta::class -> "tenantDestroyScheduled"
      ApiVersionDeprecatedMeta::class -> "apiVersionDeprecated"
      WarningTruncatedMeta::class -> "warningTruncated"
      else -> error("Unsupported warning meta type: ${metaType.simpleName}")
    }

  private fun validateProjectEmbed(project: ProjectSummary) {
    require(project.id.value.startsWith("prj_")) {
      "Expected public id prefix 'prj' but was '${project.id.value}'."
    }
    require(project.identifier.isNotBlank()) { "Project identifier is required in warning embed." }
    require(project.name.isNotBlank()) { "Project name is required in warning embed." }
  }

  private fun validateTenantEmbed(tenant: TenantSummary) {
    require(tenant.id.value.startsWith("ten_")) {
      "Expected public id prefix 'ten' but was '${tenant.id.value}'."
    }
    require(tenant.name.isNotBlank()) { "Tenant name is required in warning embed." }
    require(tenant.slug.isNotBlank()) { "Tenant slug is required in warning embed." }
  }

  private fun validateApiVersionDeprecated(meta: ApiVersionDeprecatedMeta) {
    require(meta.requestedVersion.matches(ApiVersionPattern)) {
      "requestedVersion must be yyyy-MM-dd."
    }
    require(meta.currentVersion.matches(ApiVersionPattern)) {
      "currentVersion must be yyyy-MM-dd."
    }
    meta.sunsetOn?.let {
      require(it.matches(IsoDatePattern)) { "sunsetOn must be yyyy-MM-dd." }
    }
  }

  fun validatePublicId(value: String, prefix: String) = requirePublicId(value, prefix)

  private fun requirePublicId(value: String, prefix: String) {
    PublicId(value)
    require(value.startsWith("${prefix}_")) {
      "Expected public id prefix '$prefix' but was '$value'."
    }
  }

  fun validateUserRef(userId: String) {
    requirePublicId(userId, "usr")
  }
}
