import java.time.Instant
import org.gradle.api.tasks.SourceSetContainer

plugins {
    java
    id("org.springframework.boot") version "3.5.14" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.archdox"
    version = providers.gradleProperty("archdoxVersion")
        .orElse(providers.environmentVariable("ARCHDOX_VERSION"))
        .orElse("0.0.1-SNAPSHOT")
        .get()
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    val generatedBuildInfoDir = layout.buildDirectory.dir("generated/archdox-build-info")
    val generateArchDoxBuildInfo by tasks.registering {
        val outputFile = generatedBuildInfoDir.map { it.file("META-INF/archdox/${project.name}-build.properties") }
        outputs.file(outputFile)
        doLast {
            val file = outputFile.get().asFile
            file.parentFile.mkdirs()
            file.writeText(
                """
                module=${project.name}
                version=${project.version}
                git.commit=${gitOutput("rev-parse", "--short=12", "HEAD")}
                git.branch=${gitOutput("rev-parse", "--abbrev-ref", "HEAD")}
                build.time=${Instant.now()}
                """.trimIndent()
            )
        }
    }

    extensions.configure<SourceSetContainer>("sourceSets") {
        named("main") {
            resources.srcDir(generatedBuildInfoDir)
        }
    }

    tasks.named("processResources") {
        dependsOn(generateArchDoxBuildInfo)
    }
}

fun gitOutput(vararg args: String): String {
    return try {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(rootDir)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output.trim().ifBlank { "unknown" }
    } catch (_: Exception) {
        "unknown"
    }
}

project(":flower-bloom-adapter") {
    dependencies {
        "implementation"(project(":flower-core"))
        "implementation"(project(":bloom-core"))
    }
}

project(":bloom-spring") {
    dependencies {
        "implementation"(project(":bloom-core"))
        "compileOnly"("org.springframework:spring-context:6.2.18")
    }
}

listOf(":bloom-core", ":bloom-spring", ":flower-core", ":flower-bloom-adapter").forEach { moduleName ->
    project(moduleName) {
        tasks.matching { it.name in setOf("compileTestJava", "processTestResources", "testClasses") }
            .configureEach {
                enabled = false
            }
        tasks.withType<Test> {
            enabled = false
        }
    }
}
