package ink.doa.workbench.worker.messaging

fun interface DomainEventHandler<T : Any> {
  suspend fun handle(payload: T)
}
