package doa.ink.workbench.web.support

import doa.ink.workbench.agile.project.ProjectResolver
import doa.ink.workbench.tenant.instance.InstanceContextProvider
import doa.ink.workbench.tenant.instance.InstanceProperties
import io.mockk.mockk
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

  @Bean fun projectResolver(): ProjectResolver = mockk(relaxed = true)
}
