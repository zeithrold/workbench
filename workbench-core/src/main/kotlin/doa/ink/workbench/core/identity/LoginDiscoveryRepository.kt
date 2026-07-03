package doa.ink.workbench.core.identity

import doa.ink.workbench.core.identity.model.TenantLoginOption
import doa.ink.workbench.core.identity.model.UserRecord

interface LoginDiscoveryRepository {
  suspend fun listLoginOptionsForIdentifier(normalizedIdentifier: String): List<TenantLoginOption>

  suspend fun findUserByMethodAndSubject(
    loginMethodCode: String,
    normalizedSubject: String,
  ): UserRecord?
}
