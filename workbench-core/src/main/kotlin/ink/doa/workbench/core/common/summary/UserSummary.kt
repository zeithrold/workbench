package ink.doa.workbench.core.common.summary

import ink.doa.workbench.core.identity.model.UserRecord

data class UserSummary(
  val id: String,
  val displayName: String,
  val primaryEmail: String?,
) {
  companion object {
    fun from(record: UserRecord): UserSummary =
      UserSummary(
        id = record.apiId.value,
        displayName = record.displayName,
        primaryEmail = record.primaryEmail,
      )
  }
}
