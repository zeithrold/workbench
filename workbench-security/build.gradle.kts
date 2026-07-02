plugins {
  kotlin("plugin.spring")
}

dependencies {
  implementation(project(":workbench-core"))
  implementation(project(":workbench-service"))
  implementation("org.springframework.boot:spring-boot-starter-security")
}
