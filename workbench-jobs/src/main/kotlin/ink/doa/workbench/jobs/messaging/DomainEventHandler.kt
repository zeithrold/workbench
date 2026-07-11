package ink.doa.workbench.jobs.messaging

fun interface DomainEventHandler<T : Any> {
  suspend fun handle(payload: T)
}
