package one.ztd.workbench.tenant.common.summary

import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.kernel.common.warning.meta.TenantWarningEmbed
import one.ztd.workbench.tenant.model.TenantRecord

/**
 * Wire-safe tenant embed. [id] is a typed public id (`ten_` + ULID). Prefer [from] for production
 * mapping.
 */
data class TenantSummary(
  override val id: PublicId,
  override val name: String,
  override val slug: String,
) : TenantWarningEmbed {
  companion object {
    fun from(record: TenantRecord): TenantSummary =
      TenantSummary(
        id = record.apiId,
        name = record.name,
        slug = record.slug,
      )
  }
}
