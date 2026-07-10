import com.github.gradle.node.pnpm.task.PnpmTask

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

tasks.register("prepareFrontendEnv") {
    group = "build"
    description = "Copy workbench-frontend/.env.example to .env when missing (SvelteKit public env types)."
    val envExample = layout.projectDirectory.file(".env.example")
    val envFile = layout.projectDirectory.file(".env")
    inputs.file(envExample)
    outputs.file(envFile)
    doLast {
        val target = envFile.asFile
        if (!target.exists()) {
            envExample.asFile.copyTo(target)
        }
    }
}

tasks.register<PnpmTask>("pnpmDev") {
    dependsOn("prepareFrontendEnv", "pnpmInstall")
    args.set(listOf("dev", "--host", "0.0.0.0"))
}

tasks.register<PnpmTask>("pnpmLint") {
    dependsOn("prepareFrontendEnv", "pnpmInstall")
    args.set(listOf("lint"))
}

tasks.register<PnpmTask>("pnpmTest") {
    dependsOn("prepareFrontendEnv", "pnpmInstall")
    args.set(listOf("test:unit"))
}

tasks.register<PnpmTask>("pnpmBuild") {
    dependsOn("prepareFrontendEnv", "pnpmInstall")
    args.set(listOf("build"))
}

tasks.register<PnpmTask>("pnpmCoverageUnit") {
    dependsOn("prepareFrontendEnv", "pnpmInstall")
    args.set(listOf("coverage:unit"))
}

tasks.register<PnpmTask>("pnpmCoverageStorybook") {
    dependsOn("prepareFrontendEnv", "pnpmInstall")
    args.set(listOf("coverage:storybook"))
}

tasks.register<PnpmTask>("pnpmCoverageFull") {
    dependsOn("prepareFrontendEnv", "pnpmInstall")
    args.set(listOf("coverage:full"))
}

tasks.register<PnpmTask>("pnpmCoverageE2e") {
    dependsOn("prepareFrontendEnv", "pnpmInstall")
    args.set(listOf("coverage:e2e"))
}

tasks.register<PnpmTask>("pnpmStorybookBuild") {
    dependsOn("prepareFrontendEnv", "pnpmInstall")
    args.set(listOf("storybook:build"))
}

tasks.register<PnpmTask>("pnpmStorybookTest") {
    dependsOn("prepareFrontendEnv", "pnpmInstall")
    args.set(listOf("storybook:test"))
}

tasks.register<PnpmTask>("pnpmE2e") {
    dependsOn("prepareFrontendEnv", "pnpmInstall")
    args.set(listOf("test:e2e"))
}

tasks.register<PnpmTask>("pnpmE2eStack") {
    dependsOn(
        "prepareFrontendEnv",
        "pnpmInstall",
        "pnpmBuild",
        ":workbench-web:bootJar",
        ":workbench-worker:bootJar",
        ":ciPrepareKoverE2eAgent",
    )
    args.set(listOf("test:e2e:stack"))
}

tasks.register("e2eCheck") {
    group = "verification"
    description = "Full-stack E2E via Testcontainers Compose, Spring Boot API, and Playwright."
    dependsOn("pnpmE2eStack", ":ciGenerateKoverE2eReport")
}

gradle.projectsEvaluated {
    rootProject.tasks.named("ciGenerateKoverE2eReport").configure {
        mustRunAfter(tasks.named("pnpmE2eStack"))
    }
}

tasks.register("quickCheck") {
    group = "verification"
    description = "Fast frontend verification: lint and unit tests."
    dependsOn("pnpmLint", "pnpmTest")
}

tasks.named("check") {
    dependsOn("pnpmLint", "pnpmTest", "pnpmStorybookBuild", "pnpmStorybookTest")
}

tasks.named("assemble") {
    dependsOn("pnpmBuild")
}
