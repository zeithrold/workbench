package one.ztd.workbench.web.api.context

import java.util.UUID
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.ids.PublicId

data class UserContextSummary(
  val id: UUID,
  val publicId: PublicId,
  val displayName: String,
  val primaryEmail: String?,
) {
  companion object {
    fun from(record: UserRecord): UserContextSummary =
      UserContextSummary(
        id = record.id,
        publicId = record.apiId,
        displayName = record.displayName,
        primaryEmail = record.primaryEmail,
      )
  }
}
