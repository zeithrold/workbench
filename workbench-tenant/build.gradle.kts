plugins {
  kotlin("plugin.spring")
  kotlin("kapt")
}

dependencies {
  kapt(
    platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
  )
  kapt("org.springframework.boot:spring-boot-configuration-processor")
  implementation(project(":workbench-core"))
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot")
  implementation("org.slf4j:slf4j-api")
}
