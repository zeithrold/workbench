package ink.doa.workbench.core.workitem.stream

interface WorkItemEventRepository {
  suspend fun <T : Any> append(command: AppendWorkItemEventCommand<T>): WorkItemEventRecord
}
