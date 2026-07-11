package ink.doa.workbench.jobs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.mock.env.MockEnvironment

class JobsEnabledConditionTest :
  StringSpec({
    val metadata = mockk<AnnotatedTypeMetadata>()

    "compact transports enable jobs in web" {
      conditionMatches("workbench-web", "postgres", metadata) shouldBe true
      conditionMatches("workbench-web", "redis-streams", metadata) shouldBe true
    }

    "kafka enables jobs only in worker" {
      conditionMatches("workbench-web", "kafka", metadata) shouldBe false
      conditionMatches("workbench-worker", "kafka", metadata) shouldBe true
    }
  })

private fun conditionMatches(
  applicationName: String,
  transport: String,
  metadata: AnnotatedTypeMetadata,
): Boolean {
  val environment =
    MockEnvironment()
      .withProperty("spring.application.name", applicationName)
      .withProperty("workbench.messaging.transport", transport)
  val context = mockk<ConditionContext> { every { this@mockk.environment } returns environment }
  return JobsEnabledCondition().matches(context, metadata)
}
