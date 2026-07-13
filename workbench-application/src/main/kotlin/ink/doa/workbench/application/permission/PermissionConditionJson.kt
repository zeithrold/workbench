package ink.doa.workbench.application.permission

object PermissionConditionJson {
  fun validateAndCanonicalize(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return PermissionConditionParser.canonicalizeOrThrow(raw)
  }
}
