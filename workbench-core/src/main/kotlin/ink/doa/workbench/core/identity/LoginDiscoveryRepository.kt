package ink.doa.workbench.core.identity

import ink.doa.workbench.core.identity.model.TenantLoginOption
import ink.doa.workbench.core.identity.model.UserRecord

interface LoginDiscoveryRepository {
  suspend fun listLoginOptionsForIdentifier(normalizedIdentifier: String): List<TenantLoginOption>

  suspend fun findUserByMethodAndSubject(
    loginMethodCode: String,
    normalizedSubject: String,
  ): UserRecord?
}
