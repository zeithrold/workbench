package ink.doa.workbench.data.messaging

import ink.doa.workbench.core.messaging.DomainEventDecoder
import ink.doa.workbench.core.messaging.DomainEventEncoder
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MessagingConfiguration {
  @Bean fun domainEventEncoder(clock: Clock): DomainEventEncoder = DomainEventEncoder(clock)

  @Bean fun domainEventDecoder(): DomainEventDecoder = DomainEventDecoder()
}
