package ink.doa.workbench.web.api.context

import ink.doa.workbench.identity.model.UserRecord
import ink.doa.workbench.kernel.common.ids.PublicId
import java.util.UUID

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
