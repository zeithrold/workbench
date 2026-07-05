package ink.doa.workbench.core.storage

class BlobStorageObjectNotFoundException(val key: String) :
  RuntimeException("Blob object not found: $key")
