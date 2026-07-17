package one.ztd.workbench.data.repository.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.identity.model.AuditEventResult
import one.ztd.workbench.identity.model.AuthEventType
import one.ztd.workbench.identity.model.InvitationType
import one.ztd.workbench.identity.model.LoginMethodKind
import one.ztd.workbench.identity.model.TenantMemberStatus
import one.ztd.workbench.tenant.model.TenantStatus

class ExposedIdentityEnumMappingTest :
  StringSpec({
    "maps identity enums from database values" {
      tenantMemberStatusOf("active") shouldBe TenantMemberStatus.ACTIVE
      tenantStatusOf("destroying") shouldBe TenantStatus.DESTROYING
      invitationTypeOf("tenant_member") shouldBe InvitationType.TENANT_MEMBER
      loginMethodKindOf("oidc") shouldBe LoginMethodKind.OIDC
      authEventTypeOf("login_success") shouldBe AuthEventType.LOGIN_SUCCESS
      auditEventResultOf("failure") shouldBe AuditEventResult.FAILURE
    }

    "rejects unknown enum values" {
      shouldThrow<NoSuchElementException> { tenantMemberStatusOf("unknown") }
      shouldThrow<NoSuchElementException> { loginMethodKindOf("magic") }
    }
  })
