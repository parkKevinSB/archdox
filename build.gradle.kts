plugins {
    java
    id("org.springframework.boot") version "3.5.14" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.archdox"
    version = "0.0.1-SNAPSHOT"
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
