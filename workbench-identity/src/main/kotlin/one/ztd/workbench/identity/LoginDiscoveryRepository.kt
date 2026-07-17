package one.ztd.workbench.identity

import one.ztd.workbench.identity.model.TenantLoginOption
import one.ztd.workbench.identity.model.UserRecord

interface LoginDiscoveryRepository {
  suspend fun listLoginOptionsForIdentifier(normalizedIdentifier: String): List<TenantLoginOption>

  suspend fun findUserByMethodAndSubject(
    loginMethodCode: String,
    normalizedSubject: String,
  ): UserRecord?
}
