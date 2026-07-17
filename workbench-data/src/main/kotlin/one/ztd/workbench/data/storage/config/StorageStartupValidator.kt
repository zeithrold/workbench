package one.ztd.workbench.data.storage.config

import org.springframework.beans.factory.InitializingBean

class StorageStartupValidator(private val properties: StorageProperties) : InitializingBean {
  override fun afterPropertiesSet() {
    require(properties.endpoint.isNotBlank()) {
      "workbench.storage.endpoint is required; in-memory storage is not supported"
    }
  }
}
