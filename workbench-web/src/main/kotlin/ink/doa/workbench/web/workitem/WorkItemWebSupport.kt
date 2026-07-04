package ink.doa.workbench.web.workitem

import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode

internal fun actorUserId(tenantContext: TenantRequestContext) =
  tenantContext.actor?.id
    ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
