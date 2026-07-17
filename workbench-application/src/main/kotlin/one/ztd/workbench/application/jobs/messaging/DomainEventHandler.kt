package one.ztd.workbench.application.jobs.messaging

fun interface DomainEventHandler<T : Any> {
  suspend fun handle(payload: T)
}
