import java.nio.file.Files
import java.security.MessageDigest
import org.gradle.api.tasks.bundling.Zip

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":domain-shared"))
    implementation(project(":document-engine"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation(platform("software.amazon.awssdk:bom:2.25.70"))
    implementation("software.amazon.awssdk:s3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val archdoxReleaseChannel = providers.gradleProperty("archdoxReleaseChannel")
    .orElse(providers.environmentVariable("ARCHDOX_RELEASE_CHANNEL"))
    .orElse("stable")
val archdoxReleasePlatform = providers.gradleProperty("archdoxPlatform")
    .orElse(providers.environmentVariable("ARCHDOX_RELEASE_PLATFORM"))
    .orElse("windows-x64")

tasks.register<Zip>("agentRuntimePackage") {
    val channel = archdoxReleaseChannel.get()
    val platform = archdoxReleasePlatform.get()
    group = "distribution"
    description = "Packages the ArchDox Agent runtime for release distribution."
    dependsOn("bootJar")
    archiveFileName.set("archdox-agent-runtime-$platform-${project.version}.zip")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir(
        "archdox-releases/agent-runtime/$channel/$platform/${project.version}"))
    from(tasks.named("bootJar")) {
        rename { "archdox-agent-${project.version}.jar" }
    }
}

tasks.register("agentRuntimePackageSha256") {
    group = "distribution"
    description = "Writes the SHA-256 checksum for the Agent runtime package."
    dependsOn("agentRuntimePackage")
    doLast {
        val packageFile = tasks.named<Zip>("agentRuntimePackage").get().archiveFile.get().asFile.toPath()
        val checksumFile = packageFile.resolveSibling(packageFile.fileName.toString() + ".sha256")
        Files.writeString(checksumFile, sha256Hex(packageFile) + "  " + packageFile.fileName + System.lineSeparator())
    }
}

fun sha256Hex(file: java.nio.file.Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
