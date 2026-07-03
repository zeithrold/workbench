package ink.doa.workbench.core.common.context

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.TenantRecord
import java.util.UUID

data class TenantContextSummary(
  val id: UUID,
  val publicId: PublicId,
  val name: String,
  val slug: String,
) {
  companion object {
    fun from(record: TenantRecord): TenantContextSummary =
      TenantContextSummary(
        id = record.id,
        publicId = record.apiId,
        name = record.name,
        slug = record.slug,
      )
  }
}
