package ink.doa.workbench.core.common.summary

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.identity.model.UserRecord

/**
 * Wire-safe user embed. [id] is a typed public id (`usr_` + ULID). Prefer [from] for production
 * mapping.
 */
data class UserSummary(
  val id: PublicId,
  val displayName: String,
  val primaryEmail: String?,
) {
  companion object {
    fun from(record: UserRecord): UserSummary =
      UserSummary(
        id = record.apiId,
        displayName = record.displayName,
        primaryEmail = record.primaryEmail,
      )
  }
}
