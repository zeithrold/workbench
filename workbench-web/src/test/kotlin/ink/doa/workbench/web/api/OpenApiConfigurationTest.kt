package ink.doa.workbench.web.api

import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.core.identity.model.AuthenticatedPrincipal
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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
