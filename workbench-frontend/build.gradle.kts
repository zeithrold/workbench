plugins {
    base
    alias(libs.plugins.node)
}

node {
    version.set("24.15.0")
    download.set(true)
    distBaseUrl.unset()
    pnpmVersion.set("10.33.0")
    nodeProjectDir.set(projectDir)
}

tasks.register<com.github.gradle.node.pnpm.task.PnpmTask>("pnpmDev") {
    dependsOn("pnpmInstall")
    args.set(listOf("dev", "--host", "0.0.0.0"))
}

tasks.register<com.github.gradle.node.pnpm.task.PnpmTask>("pnpmLint") {
    dependsOn("pnpmInstall")
    args.set(listOf("lint"))
}

tasks.register<com.github.gradle.node.pnpm.task.PnpmTask>("pnpmTest") {
    dependsOn("pnpmInstall")
    args.set(listOf("test:unit"))
}

tasks.register<com.github.gradle.node.pnpm.task.PnpmTask>("pnpmBuild") {
    dependsOn("pnpmInstall")
    args.set(listOf("build"))
}

tasks.register<com.github.gradle.node.pnpm.task.PnpmTask>("pnpmCoverage") {
    dependsOn("pnpmInstall")
    args.set(listOf("coverage"))
}

tasks.register("snapshotFrontendUnitCoverage") {
    group = "verification"
    description = "Copies Vitest LCOV into coverage/unit/ for unit-vs-full reporting."
    dependsOn("pnpmCoverage")
    doLast {
        val lcov = layout.projectDirectory.file("coverage/lcov.info").asFile
        if (lcov.isFile) {
            val unitDir = layout.projectDirectory.dir("coverage/unit").asFile
            unitDir.mkdirs()
            lcov.copyTo(unitDir.resolve("lcov.info"), overwrite = true)
        }
    }
}

tasks.register<com.github.gradle.node.pnpm.task.PnpmTask>("pnpmE2e") {
    dependsOn("pnpmInstall")
    args.set(listOf("test:e2e"))
}

tasks.register("quickCheck") {
    dependsOn("pnpmLint")
}

tasks.named("check") {
    dependsOn("pnpmLint", "pnpmTest")
}

tasks.named("assemble") {
    dependsOn("pnpmBuild")
}
