plugins {
    `java-library`
}

dependencies {
    api(project(":flower-core"))
    api("io.github.parkkevinsb.flower.ai.harness:flower-ai-harness-core:0.1.0-SNAPSHOT") {
        exclude(group = "io.github.parkkevinsb.flower", module = "flower-core")
    }

    implementation("io.github.parkkevinsb.flower.ai.harness:flower-ai-harness-validator-jackson:0.1.0-SNAPSHOT") {
        exclude(group = "io.github.parkkevinsb.flower", module = "flower-core")
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    testImplementation("io.github.parkkevinsb.flower.ai.harness:flower-ai-harness-test:0.1.0-SNAPSHOT") {
        exclude(group = "io.github.parkkevinsb.flower", module = "flower-core")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}
