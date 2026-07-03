pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "workbench"

include(
    ":workbench-core",
    ":workbench-service",
    ":workbench-agile",
    ":workbench-data",
    ":workbench-security",
    ":workbench-web",
    ":workbench-worker",
    ":workbench-frontend",
)
