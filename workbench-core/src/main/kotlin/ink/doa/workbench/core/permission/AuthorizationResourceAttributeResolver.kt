package ink.doa.workbench.core.permission

import ink.doa.workbench.core.permission.model.AuthorizationRequest
import ink.doa.workbench.core.permission.model.AuthorizationResource

interface AuthorizationResourceAttributeResolver {
  suspend fun supports(resource: AuthorizationResource): Boolean

  suspend fun resolveAttributes(request: AuthorizationRequest): Map<String, String>
}
