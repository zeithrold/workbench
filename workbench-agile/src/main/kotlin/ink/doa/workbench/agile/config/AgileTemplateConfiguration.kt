package ink.doa.workbench.agile.config

import ink.doa.workbench.agile.workitem.access.AccessConditionEvaluator
import ink.doa.workbench.agile.workitem.template.TransitionFieldsParser
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AgileTemplateConfiguration {
  @Bean fun transitionFieldsParser(): TransitionFieldsParser = TransitionFieldsParser()

  @Bean fun accessConditionEvaluator(): AccessConditionEvaluator = AccessConditionEvaluator()
}
