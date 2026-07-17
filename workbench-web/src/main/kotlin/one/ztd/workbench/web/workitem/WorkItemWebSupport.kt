package one.ztd.workbench.web.workitem

import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.web.api.context.TenantRequestContext

internal fun actorUserId(tenantContext: TenantRequestContext) =
  tenantContext.actor?.id
    ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
