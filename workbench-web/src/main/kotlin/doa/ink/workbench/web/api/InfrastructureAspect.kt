package doa.ink.workbench.web.api

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class InfrastructureAspect {
  private val logger = LoggerFactory.getLogger(javaClass)

  @Around("@annotation(doa.ink.workbench.web.api.Audit)")
  @Suppress("TooGenericExceptionCaught")
  fun audit(joinPoint: ProceedingJoinPoint): Any? {
    val started = System.nanoTime()
    return try {
      joinPoint.proceed().also {
        logger.info(
          "audit_result method={} result=success durationMs={}",
          joinPoint.signature.toShortString(),
          elapsedMillis(started),
        )
      }
    } catch (error: RuntimeException) {
      logger.warn(
        "audit_result method={} result=failure durationMs={}",
        joinPoint.signature.toShortString(),
        elapsedMillis(started),
        error,
      )
      throw error
    }
  }

  @Around("@annotation(doa.ink.workbench.web.api.PublishEvent)")
  fun publishEvent(joinPoint: ProceedingJoinPoint): Any? = joinPoint.proceed()

  private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000
}
