package doa.ink.workbench.data.persistence

import javax.sql.DataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DatabaseConfiguration {
  @Bean fun exposedDatabase(dataSource: DataSource): Database = Database.connect(dataSource)
}
