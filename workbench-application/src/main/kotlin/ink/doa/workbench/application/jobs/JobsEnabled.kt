package ink.doa.workbench.application.jobs

import ink.doa.workbench.application.jobs.messaging.MessagingTransport
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Conditional(JobsEnabledCondition::class)
annotation class JobsEnabled

class JobsEnabledCondition : Condition {
  override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
    val environment = context.environment
    val transport =
      environment.getProperty("workbench.messaging.transport", MessagingTransport.POSTGRES.name)
    val applicationName = environment.getProperty("spring.application.name", "")
    return !transport.equals(MessagingTransport.KAFKA.name, ignoreCase = true) ||
      applicationName == "workbench-worker"
  }
}
