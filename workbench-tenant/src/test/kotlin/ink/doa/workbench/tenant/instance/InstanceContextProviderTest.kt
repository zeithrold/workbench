package ink.doa.workbench.tenant.instance

import ink.doa.workbench.core.common.context.InstanceContextSummary
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InstanceContextProviderTest :
  StringSpec({
    "current uses configured instance id and name" {
      val provider =
        InstanceContextProvider(
          instanceProperties = InstanceProperties(id = "instance-1", name = "Workbench Dev"),
          applicationName = "workbench-web",
        )

      provider.current() shouldBe InstanceContextSummary(id = "instance-1", name = "Workbench Dev")
    }

    "current falls back to application name when instance name is blank" {
      val provider =
        InstanceContextProvider(
          instanceProperties = InstanceProperties(id = "instance-1", name = "  "),
          applicationName = "workbench-web",
        )

      provider.current().name shouldBe "workbench-web"
    }

    "current falls back to host name when instance id is blank" {
      val provider =
        InstanceContextProvider(
          instanceProperties = InstanceProperties(id = null, name = "Workbench"),
          applicationName = "workbench-web",
        )

      provider.current().id.isNotBlank() shouldBe true
      provider.current().name shouldBe "Workbench"
    }
  })
