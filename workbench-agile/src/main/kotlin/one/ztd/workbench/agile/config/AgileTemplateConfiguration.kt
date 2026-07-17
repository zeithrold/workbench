package one.ztd.workbench.agile.config

import one.ztd.workbench.agile.workitem.access.AccessConditionEvaluator
import one.ztd.workbench.agile.workitem.template.TransitionFieldsParser
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AgileTemplateConfiguration {
  @Bean fun transitionFieldsParser(): TransitionFieldsParser = TransitionFieldsParser()

  @Bean fun accessConditionEvaluator(): AccessConditionEvaluator = AccessConditionEvaluator()
}
