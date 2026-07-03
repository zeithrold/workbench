package doa.ink.workbench.web.support

import doa.ink.workbench.service.instance.InstanceContextProvider
import doa.ink.workbench.service.instance.InstanceProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class ContextWebMvcSupport {
  @Bean
  fun instanceContextProvider(): InstanceContextProvider =
    InstanceContextProvider(
      instanceProperties = InstanceProperties(id = "test-instance", name = "workbench"),
      applicationName = "workbench",
    )
}
