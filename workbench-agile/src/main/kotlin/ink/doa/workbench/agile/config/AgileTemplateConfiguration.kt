package ink.doa.workbench.agile.config

import ink.doa.workbench.core.workitem.template.TransitionFieldsParser
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AgileTemplateConfiguration {
  @Bean fun transitionFieldsParser(): TransitionFieldsParser = TransitionFieldsParser()
}
