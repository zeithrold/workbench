package doa.ink.workbench.security.identity

data class ClientContext(
  val ipAddress: String?,
  val userAgent: String?,
)
