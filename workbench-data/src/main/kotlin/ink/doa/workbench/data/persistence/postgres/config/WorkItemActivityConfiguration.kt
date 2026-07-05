package ink.doa.workbench.data.persistence.postgres.config

import ink.doa.workbench.core.workitem.activity.WorkItemActivityCodec
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WorkItemActivityConfiguration {
  @Bean fun workItemActivityCodec(): WorkItemActivityCodec = WorkItemActivityCodec()
}
