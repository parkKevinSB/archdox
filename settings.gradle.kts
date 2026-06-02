pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "archdox"

include("cloud-api")
include("archdox-worker")
include("archdox-ai-harness")
include("document-engine")
include("domain-shared")
include("archdox-agent")

include("bloom-core")
project(":bloom-core").projectDir = file("libs/bloom/bloom-core")

include("bloom-spring")
project(":bloom-spring").projectDir = file("libs/bloom/bloom-spring")

include("flower-core")
project(":flower-core").projectDir = file("libs/flower/flower-core")

include("flower-bloom-adapter")
project(":flower-bloom-adapter").projectDir = file("libs/flower/flower-bloom-adapter")
