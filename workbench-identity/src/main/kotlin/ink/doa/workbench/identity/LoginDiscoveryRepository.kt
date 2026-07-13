package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.TenantLoginOption
import ink.doa.workbench.identity.model.UserRecord

interface LoginDiscoveryRepository {
  suspend fun listLoginOptionsForIdentifier(normalizedIdentifier: String): List<TenantLoginOption>

  suspend fun findUserByMethodAndSubject(
    loginMethodCode: String,
    normalizedSubject: String,
  ): UserRecord?
}
