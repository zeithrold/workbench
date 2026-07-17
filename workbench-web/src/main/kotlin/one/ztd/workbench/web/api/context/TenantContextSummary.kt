package one.ztd.workbench.web.api.context

import java.util.UUID
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.tenant.model.TenantRecord

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
