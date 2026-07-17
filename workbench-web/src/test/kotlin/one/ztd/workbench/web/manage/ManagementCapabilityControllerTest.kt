package one.ztd.workbench.web.manage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import one.ztd.workbench.application.permission.ManagementCapabilityService
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.context.InstanceContextSummary
import one.ztd.workbench.kernel.common.ids.PublicId
import one.ztd.workbench.web.api.context.ApiVersion
import one.ztd.workbench.web.api.context.InstanceRequestContext
import one.ztd.workbench.web.api.context.TenantContextSummary
import one.ztd.workbench.web.api.context.TenantRequestContext

class ManagementCapabilityControllerTest :
  StringSpec({
    "returns instance and tenant summaries with effective actions" {
      val capabilities = mockk<ManagementCapabilityService>()
      coEvery { capabilities.instanceCapabilities(PRINCIPAL) } returns listOf("instance.read")
      coEvery { capabilities.tenantCapabilities(PRINCIPAL, TENANT_ID) } returns
        listOf("tenant.read", "tenant.update")
      val controller = ManagementCapabilityController(capabilities)

      val instance = runBlocking { controller.instance(PRINCIPAL, INSTANCE_CONTEXT) }
      val tenant = runBlocking { controller.tenant(PRINCIPAL, TENANT_CONTEXT) }

      instance.scope shouldBe "INSTANCE"
      instance.instance shouldBe INSTANCE
      instance.actions shouldBe listOf("instance.read")
      tenant.scope shouldBe "TENANT"
      tenant.tenant.slug shouldBe "acme"
      tenant.actions shouldBe listOf("tenant.read", "tenant.update")
    }
  }) {
  private companion object {
    val TENANT_ID: UUID = UUID.randomUUID()
    val INSTANCE = InstanceContextSummary("ins_01JABCDEFGHJKMNPQRSTVWXYZ0", "Workbench")
    val PRINCIPAL =
      AuthenticatedPrincipal(
        user =
          UserRecord(
            UUID.randomUUID(),
            PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ1"),
            "Ada",
            "ada@example.test",
          ),
        loginAccountId = UUID.randomUUID(),
        sessionId = "session",
        bearerTokenId = null,
      )
    val INSTANCE_CONTEXT =
      InstanceRequestContext(
        "request",
        ApiVersion.Default,
        null,
        Instant.parse("2026-07-15T00:00:00Z"),
        INSTANCE,
      )
    val TENANT_CONTEXT =
      TenantRequestContext(
        "request",
        ApiVersion.Default,
        null,
        Instant.parse("2026-07-15T00:00:00Z"),
        INSTANCE,
        TenantContextSummary(
          TENANT_ID,
          PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          "Acme",
          "acme",
        ),
      )
  }
}
