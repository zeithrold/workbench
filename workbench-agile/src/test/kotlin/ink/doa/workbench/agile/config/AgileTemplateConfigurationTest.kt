package ink.doa.workbench.agile.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe

class AgileTemplateConfigurationTest :
  StringSpec({
    "configuration exposes template parser and access condition evaluator" {
      val configuration = AgileTemplateConfiguration()

      configuration.transitionFieldsParser() shouldNotBe null
      configuration.accessConditionEvaluator() shouldNotBe null
    }
  })
