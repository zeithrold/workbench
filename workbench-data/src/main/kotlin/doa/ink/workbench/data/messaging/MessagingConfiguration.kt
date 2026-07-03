package doa.ink.workbench.data.messaging

import doa.ink.workbench.core.messaging.DomainEventDecoder
import doa.ink.workbench.core.messaging.DomainEventEncoder
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MessagingConfiguration {
  @Bean fun domainEventEncoder(clock: Clock): DomainEventEncoder = DomainEventEncoder(clock)

  @Bean fun domainEventDecoder(): DomainEventDecoder = DomainEventDecoder()
}
