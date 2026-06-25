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
