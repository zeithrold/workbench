package ink.doa.workbench.data.persistence.postgres.config

import ink.doa.workbench.agile.workitem.activity.WorkItemActivityCodec
import ink.doa.workbench.agile.workitem.stream.WorkItemEventCodec
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WorkItemActivityConfiguration {
  @Bean fun workItemActivityCodec(): WorkItemActivityCodec = WorkItemActivityCodec()

  @Bean fun workItemEventCodec(): WorkItemEventCodec = WorkItemEventCodec()
}
