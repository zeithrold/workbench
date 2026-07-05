package ink.doa.workbench.data.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoroutineDispatchersConfiguration {
  @Bean @Suppress("InjectDispatcher") fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
