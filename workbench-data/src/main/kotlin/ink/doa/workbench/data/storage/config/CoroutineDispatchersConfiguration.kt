package ink.doa.workbench.data.storage.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CoroutineDispatchersConfiguration {
  @Bean(destroyMethod = "")
  @Suppress("InjectDispatcher") // Composition root that provides the injectable IO dispatcher.
  fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
