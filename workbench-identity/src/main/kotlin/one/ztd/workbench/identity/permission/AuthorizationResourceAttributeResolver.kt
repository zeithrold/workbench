package one.ztd.workbench.identity.permission

import one.ztd.workbench.identity.permission.model.AuthorizationRequest
import one.ztd.workbench.identity.permission.model.AuthorizationResource

interface AuthorizationResourceAttributeResolver {
  suspend fun supports(resource: AuthorizationResource): Boolean

  suspend fun resolveAttributes(request: AuthorizationRequest): Map<String, String>
}
