plugins {
  kotlin("plugin.spring")
}

dependencies {
  implementation(project(":workbench-kernel"))
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot")
}
