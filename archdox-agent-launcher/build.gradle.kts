import java.nio.file.Files
import java.security.MessageDigest
import org.gradle.api.tasks.bundling.Zip

plugins {
    application
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

application {
    mainClass.set("com.archdox.agent.launcher.ArchDoxAgentLauncher")
}

val archdoxReleaseChannel = providers.gradleProperty("archdoxReleaseChannel")
    .orElse(providers.environmentVariable("ARCHDOX_RELEASE_CHANNEL"))
    .orElse("stable")
val archdoxReleasePlatform = providers.gradleProperty("archdoxPlatform")
    .orElse(providers.environmentVariable("ARCHDOX_RELEASE_PLATFORM"))
    .orElse("windows-x64")

tasks.register<Zip>("launcherPackage") {
    val channel = archdoxReleaseChannel.get()
    val platform = archdoxReleasePlatform.get()
    group = "distribution"
    description = "Packages the ArchDox Agent Launcher for release distribution."
    dependsOn("installDist")
    archiveFileName.set("archdox-agent-launcher-$platform-${project.version}.zip")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir(
        "archdox-releases/agent-launcher/$channel/$platform/${project.version}"))
    into("archdox-agent-launcher") {
        from(layout.buildDirectory.dir("install/archdox-agent-launcher"))
    }
}

tasks.register("launcherPackageSha256") {
    group = "distribution"
    description = "Writes the SHA-256 checksum for the Agent Launcher package."
    dependsOn("launcherPackage")
    doLast {
        val packageFile = tasks.named<Zip>("launcherPackage").get().archiveFile.get().asFile.toPath()
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
