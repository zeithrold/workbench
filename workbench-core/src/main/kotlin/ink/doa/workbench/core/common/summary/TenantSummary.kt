package ink.doa.workbench.core.common.summary

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.TenantRecord

/**
 * Wire-safe tenant embed. [id] is a typed public id (`ten_` + ULID). Prefer [from] for production
 * mapping.
 */
data class TenantSummary(
  val id: PublicId,
  val name: String,
  val slug: String,
) {
  companion object {
    fun from(record: TenantRecord): TenantSummary =
      TenantSummary(
        id = record.apiId,
        name = record.name,
        slug = record.slug,
      )
  }
}
