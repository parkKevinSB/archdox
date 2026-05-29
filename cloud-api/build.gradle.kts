plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.7")
    }
}

val flowerAiHarnessVersion = "0.1.0-SNAPSHOT"
val vendoredHarnessDir = rootProject.file("libs/flower-ai-harness-jars")

fun harnessJar(module: String) = vendoredHarnessDir.resolve("$module-$flowerAiHarnessVersion.jar")

fun DependencyHandlerScope.addHarnessRuntimeDependencies() {
    val starterJar = harnessJar("flower-ai-harness-spring-boot-starter")
    if (starterJar.exists()) {
        listOf(
            "flower-ai-harness-core",
            "flower-ai-harness-validator-jackson",
            "flower-ai-harness-spring-ai",
            "flower-ai-harness-spring-boot-starter",
        ).forEach { module ->
            implementation(files(harnessJar(module)))
        }
    } else {
        implementation("io.github.parkkevinsb.flower.ai.harness:flower-ai-harness-spring-boot-starter:$flowerAiHarnessVersion") {
            exclude(group = "io.github.parkkevinsb.flower", module = "flower-core")
        }
        implementation("io.github.parkkevinsb.flower.ai.harness:flower-ai-harness-validator-jackson:$flowerAiHarnessVersion") {
            exclude(group = "io.github.parkkevinsb.flower", module = "flower-core")
        }
    }
}

dependencies {
    implementation(project(":domain-shared"))
    implementation(project(":archdox-ai-harness"))
    implementation(project(":document-engine"))
    implementation(project(":bloom-core"))
    implementation(project(":bloom-spring"))
    implementation(project(":flower-core"))
    implementation(project(":flower-bloom-adapter"))

    addHarnessRuntimeDependencies()
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(platform("software.amazon.awssdk:bom:2.25.70"))
    implementation("software.amazon.awssdk:s3")
    implementation("org.sejda.imageio:webp-imageio:0.1.6")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(project(":archdox-agent"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
