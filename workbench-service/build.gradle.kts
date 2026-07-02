plugins {
  kotlin("plugin.spring")
}

dependencies {
  implementation(project(":workbench-core"))
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-starter-mail")
  implementation("org.springframework:spring-web")
}
