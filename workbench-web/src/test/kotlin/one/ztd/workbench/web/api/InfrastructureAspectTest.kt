package one.ztd.workbench.web.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import one.ztd.workbench.identity.permission.model.PermissionService
import one.ztd.workbench.kernel.common.errors.PermissionDeniedException
import one.ztd.workbench.tenant.tenant.TenantOperationalGuard
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.Signature

class InfrastructureAspectTest :
  StringSpec({
    val permissionService = mockk<PermissionService>()
    val tenantOperationalGuard = mockk<TenantOperationalGuard>(relaxed = true)
    val clock = Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC)
    val aspect = InfrastructureAspect(permissionService, tenantOperationalGuard, emptyList(), clock)

    "audit proceeds and returns join point result" {
      val joinPoint = mockJoinPoint(result = "ok")

      aspect.audit(joinPoint) shouldBe "ok"
      verify { joinPoint.proceed() }
    }

    "rejectLegacyPermission throws permission denied" {
      shouldThrow<PermissionDeniedException> {
        aspect.rejectLegacyPermission()
      }
    }

    @Suppress("DEPRECATION")
    "publishEvent proceeds for deprecated annotation" {
      val joinPoint = mockJoinPoint(result = 42)

      aspect.publishEvent(joinPoint) shouldBe 42
    }
  })

private fun mockJoinPoint(result: Any?): ProceedingJoinPoint {
  val signature = mockk<Signature>()
  every { signature.toShortString() } returns "Test.method()"
  return mockk(relaxed = true) {
    every { proceed() } returns result
    every { getSignature() } returns signature
  }
}
