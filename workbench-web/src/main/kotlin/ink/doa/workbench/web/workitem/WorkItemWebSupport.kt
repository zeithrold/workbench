package ink.doa.workbench.web.workitem

import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.web.api.context.TenantRequestContext

internal fun actorUserId(tenantContext: TenantRequestContext) =
  tenantContext.actor?.id
    ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
