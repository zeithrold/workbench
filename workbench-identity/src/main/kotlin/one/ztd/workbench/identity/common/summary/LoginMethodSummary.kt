package one.ztd.workbench.identity.common.summary

import one.ztd.workbench.identity.model.LoginMethodDefinitionRecord
import one.ztd.workbench.identity.model.LoginMethodKind

data class LoginMethodSummary(
  val id: String,
  val code: String,
  val kind: LoginMethodKind,
  val name: String,
) {
  companion object {
    fun from(record: LoginMethodDefinitionRecord): LoginMethodSummary =
      LoginMethodSummary(
        id = record.apiId.value,
        code = record.code,
        kind = record.kind,
        name = record.name,
      )
  }
}
