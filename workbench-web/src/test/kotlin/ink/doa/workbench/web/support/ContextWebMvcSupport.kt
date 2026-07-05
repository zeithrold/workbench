package ink.doa.workbench.web.support

import ink.doa.workbench.agile.project.ProjectResolver
import ink.doa.workbench.tenant.instance.InstanceContextProvider
import ink.doa.workbench.tenant.instance.InstanceProperties
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class ContextWebMvcSupport {
  @Bean
  fun instanceContextProvider(): InstanceContextProvider =
    InstanceContextProvider(
      instanceProperties =
        InstanceProperties(setupToken = null, id = "test-instance", name = "workbench"),
      applicationName = "workbench",
    )

  @Bean fun projectResolver(): ProjectResolver = mockk(relaxed = true)
}
