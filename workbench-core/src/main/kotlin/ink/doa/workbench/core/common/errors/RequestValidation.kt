package ink.doa.workbench.core.common.errors

fun requireValid(
  condition: Boolean,
  errorCode: WorkbenchErrorCode,
  detail: String? = null,
) {
  if (!condition) {
    throw InvalidRequestException(errorCode, detail)
  }
}

fun requireFound(
  condition: Boolean,
  errorCode: WorkbenchErrorCode,
  detail: String? = null,
) {
  if (!condition) {
    throw ResourceNotFoundException(errorCode, detail)
  }
}
