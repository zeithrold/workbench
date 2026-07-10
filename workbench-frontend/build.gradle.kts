plugins {
    base
    alias(libs.plugins.node)
}

import com.github.gradle.node.pnpm.task.PnpmTask

node {
    version.set("24.15.0")
    download.set(true)
    distBaseUrl.unset()
    pnpmVersion.set("10.33.0")
    nodeProjectDir.set(projectDir)
}

tasks.register<PnpmTask>("pnpmDev") {
    dependsOn("pnpmInstall")
    args.set(listOf("dev", "--host", "0.0.0.0"))
}

tasks.register<PnpmTask>("pnpmLint") {
    dependsOn("pnpmInstall")
    args.set(listOf("lint"))
}

tasks.register<PnpmTask>("pnpmTest") {
    dependsOn("pnpmInstall")
    args.set(listOf("test:unit"))
}

tasks.register<PnpmTask>("pnpmBuild") {
    dependsOn("pnpmInstall")
    args.set(listOf("build"))
}

tasks.register<PnpmTask>("pnpmCoverage") {
    dependsOn("pnpmInstall")
    args.set(listOf("coverage:unit"))
}

tasks.register<PnpmTask>("pnpmCoverageUnit") {
    dependsOn("pnpmInstall")
    args.set(listOf("coverage:unit"))
}

tasks.register<PnpmTask>("pnpmCoverageStorybook") {
    dependsOn("pnpmInstall")
    args.set(listOf("coverage:storybook"))
}

tasks.register<PnpmTask>("pnpmCoverageFull") {
    dependsOn("pnpmInstall")
    args.set(listOf("coverage:full"))
}

tasks.register<PnpmTask>("pnpmCoverageE2e") {
    dependsOn("pnpmInstall")
    args.set(listOf("coverage:e2e"))
}

tasks.register<PnpmTask>("pnpmStorybookBuild") {
    dependsOn("pnpmInstall")
    args.set(listOf("storybook:build"))
}

tasks.register<PnpmTask>("pnpmStorybookTest") {
    dependsOn("pnpmInstall")
    args.set(listOf("storybook:test"))
}

tasks.register<PnpmTask>("pnpmE2e") {
    dependsOn("pnpmInstall")
    args.set(listOf("test:e2e"))
}

tasks.register<PnpmTask>("pnpmE2eStack") {
    dependsOn(
        "pnpmInstall",
        "pnpmBuild",
        ":workbench-web:bootJar",
        ":workbench-worker:bootJar",
        ":workbenchPrepareKoverE2eAgent",
    )
    args.set(listOf("test:e2e:stack"))
}

tasks.register("workbenchE2eCheck") {
    group = "verification"
    description = "Full-stack E2E via Testcontainers Compose, Spring Boot API, and Playwright."
    dependsOn("pnpmE2eStack", ":workbenchKoverE2eReport")
}

gradle.projectsEvaluated {
    rootProject.tasks.named("workbenchKoverE2eReport").configure {
        mustRunAfter(tasks.named("pnpmE2eStack"))
    }
}

tasks.register("workbenchQuickCheck") {
    dependsOn("pnpmLint", "pnpmTest")
}

tasks.named("check") {
    dependsOn("pnpmLint", "pnpmTest", "pnpmStorybookBuild", "pnpmStorybookTest")
}

tasks.named("assemble") {
    dependsOn("pnpmBuild")
}
