package ink.doa.workbench.core.common.errors

inline fun requireValid(
  condition: Boolean,
  errorCode: WorkbenchErrorCode,
  detail: String? = null,
) {
  if (!condition) {
    throw InvalidRequestException(errorCode, detail)
  }
}

inline fun requireFound(
  condition: Boolean,
  errorCode: WorkbenchErrorCode,
  detail: String? = null,
) {
  if (!condition) {
    throw ResourceNotFoundException(errorCode, detail)
  }
}
