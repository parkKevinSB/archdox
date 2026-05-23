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
include("document-engine")
include("domain-shared")
include("archdox-agent")

include("bloom-core")
project(":bloom-core").projectDir = file("../bloom/bloom-core")

include("bloom-spring")
project(":bloom-spring").projectDir = file("../bloom/bloom-spring")

include("flower-core")
project(":flower-core").projectDir = file("../flower/flower-core")

include("flower-bloom-adapter")
project(":flower-bloom-adapter").projectDir = file("../flower/flower-bloom-adapter")
