package one.ztd.workbench.web.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.web.api.context.TenantRequestContext
import org.springdoc.core.service.AbstractRequestService

class OpenApiConfigurationTest :
  StringSpec({
    beforeSpec {
      OpenApiConfiguration()
    }

    "injected request context types are ignored by springdoc" {
      AbstractRequestService.isRequestTypeToIgnore(TenantRequestContext::class.java) shouldBe true
      AbstractRequestService.isRequestTypeToIgnore(AuthenticatedPrincipal::class.java) shouldBe true
    }
  })
