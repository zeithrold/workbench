package one.ztd.workbench.data.persistence.postgres.config

import one.ztd.workbench.agile.workitem.activity.WorkItemActivityCodec
import one.ztd.workbench.agile.workitem.stream.WorkItemEventCodec
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WorkItemActivityConfiguration {
  @Bean fun workItemActivityCodec(): WorkItemActivityCodec = WorkItemActivityCodec()

  @Bean fun workItemEventCodec(): WorkItemEventCodec = WorkItemEventCodec()
}
