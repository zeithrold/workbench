package ink.doa.workbench.web.api.context

import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.tenant.model.TenantRecord
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
