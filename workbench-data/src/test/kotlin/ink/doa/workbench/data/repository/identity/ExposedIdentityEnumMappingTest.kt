package ink.doa.workbench.data.repository.identity

import ink.doa.workbench.identity.model.AuditEventResult
import ink.doa.workbench.identity.model.AuthEventType
import ink.doa.workbench.identity.model.InvitationType
import ink.doa.workbench.identity.model.LoginMethodKind
import ink.doa.workbench.identity.model.TenantMemberStatus
import ink.doa.workbench.tenant.model.TenantStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

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
