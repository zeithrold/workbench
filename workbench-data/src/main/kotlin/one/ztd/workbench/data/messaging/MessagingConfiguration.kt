package one.ztd.workbench.data.messaging

import java.time.Clock
import one.ztd.workbench.kernel.messaging.DomainEventDecoder
import one.ztd.workbench.kernel.messaging.DomainEventEncoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MessagingConfiguration {
  @Bean fun domainEventEncoder(clock: Clock): DomainEventEncoder = DomainEventEncoder(clock)

  @Bean fun domainEventDecoder(): DomainEventDecoder = DomainEventDecoder()
}
