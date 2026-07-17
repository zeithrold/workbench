package one.ztd.workbench.web.messaging

import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["workbench.messaging.transport"], havingValue = "redis-streams")
class RedisStreamsReliabilityGuard(private val redisson: RedissonClient) : ApplicationRunner {
  override fun run(args: ApplicationArguments) {
    val settings =
      redisson.script
        .eval<List<Any>>(
          RScript.Mode.READ_ONLY,
          """
          local aof = redis.call('CONFIG', 'GET', 'appendonly')[2]
          local policy = redis.call('CONFIG', 'GET', 'maxmemory-policy')[2]
          return {aof, policy}
          """
            .trimIndent(),
          RScript.ReturnType.LIST,
        )
        .map(Any::toString)
    require(settings.getOrNull(0) == "yes") {
      "Redis Streams transport requires appendonly=yes"
    }
    require(settings.getOrNull(1) == "noeviction") {
      "Redis Streams transport requires maxmemory-policy=noeviction"
    }
  }
}
