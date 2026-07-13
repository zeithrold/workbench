package ink.doa.workbench.identity.permission

import ink.doa.workbench.identity.permission.model.AuthorizationRequest
import ink.doa.workbench.identity.permission.model.AuthorizationResource

interface AuthorizationResourceAttributeResolver {
  suspend fun supports(resource: AuthorizationResource): Boolean

  suspend fun resolveAttributes(request: AuthorizationRequest): Map<String, String>
}
