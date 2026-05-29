plugins {
    `java-library`
}

val flowerAiHarnessVersion = "0.1.0-SNAPSHOT"
val vendoredHarnessDir = rootProject.file("libs/flower-ai-harness-jars")

fun harnessJar(module: String) = vendoredHarnessDir.resolve("$module-$flowerAiHarnessVersion.jar")

fun DependencyHandlerScope.addHarnessDependency(configurationName: String, module: String) {
    val jar = harnessJar(module)
    if (jar.exists()) {
        add(configurationName, files(jar))
    } else {
        add(configurationName, "io.github.parkkevinsb.flower.ai.harness:$module:$flowerAiHarnessVersion") {
            exclude(group = "io.github.parkkevinsb.flower", module = "flower-core")
        }
    }
}

dependencies {
    api(project(":flower-core"))
    addHarnessDependency("api", "flower-ai-harness-core")

    addHarnessDependency("implementation", "flower-ai-harness-validator-jackson")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    addHarnessDependency("testImplementation", "flower-ai-harness-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}
