package one.ztd.workbench.kernel.storage

class BlobStorageObjectNotFoundException(val key: String) :
  RuntimeException("Blob object not found: $key")
