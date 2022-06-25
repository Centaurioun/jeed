import java.io.File
import java.io.StringWriter
import java.util.Properties

plugins {
    kotlin("jvm")
    application
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.palantir.docker") version "0.34.0"
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
    id("com.google.devtools.ksp")
}
dependencies {
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.13.0")

    implementation(project(":core"))
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.0")

    implementation("io.ktor:ktor-server-netty:2.0.2")
    implementation("io.ktor:ktor-server-cors:2.0.2")
    implementation("io.ktor:ktor-server-content-negotiation:2.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
    implementation("com.squareup.moshi:moshi-kotlin:1.13.0")
    implementation("com.github.cs125-illinois:ktor-moshi:2022.4.0")
    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("com.uchuhimo:konf-core:1.1.2")
    implementation("com.uchuhimo:konf-yaml:1.1.2")
    implementation("io.github.microutils:kotlin-logging:2.1.23")
    implementation("com.github.cs125-illinois:libcs1:2022.6.1")
    implementation("com.beyondgrader.resource-agent:jeedplugin:2022.6.5")

    testImplementation("io.kotest:kotest-runner-junit5:5.3.1")
    testImplementation("io.kotest:kotest-assertions-ktor:4.4.3")
    testImplementation("io.ktor:ktor-server-test-host:2.0.2")
}
application {
    @Suppress("DEPRECATION")
    mainClassName = "edu.illinois.cs.cs125.jeed.server.MainKt"
}
docker {
    name = "cs125/jeed"
    @Suppress("DEPRECATION")
    tags("latest")
    files(tasks["shadowJar"].outputs)
}
tasks.test {
    useJUnitPlatform()
    val agentJarPath = configurations["runtimeClasspath"].resolvedConfiguration.resolvedArtifacts.find {
        it.moduleVersion.id.group == "com.beyondgrader.resource-agent" && it.moduleVersion.id.name == "agent"
    }!!.file.absolutePath
    jvmArgs("-javaagent:$agentJarPath")
    systemProperties["logback.configurationFile"] = File(projectDir, "src/test/resources/logback-test.xml").absolutePath
    environment["JEED_USE_CACHE"] = "true"
}
task("createProperties") {
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/edu.illinois.cs.cs125.jeed.server.version")
            .printWriter().use { printWriter ->
                printWriter.print(
                    StringWriter().also { properties.store(it, null) }.buffer.toString()
                        .lines().drop(1).joinToString(separator = "\n").trim()
                )
            }
    }
}
tasks.processResources {
    dependsOn("createProperties")
}
publishing {
    publications {
        create<MavenPublication>("server") {
            from(components["java"])
        }
    }
}
kotlin {
    kotlinDaemonJvmArgs = listOf("-Dfile.encoding=UTF-8")
}
tasks.shadowJar {
    manifest {
        attributes["Launcher-Agent-Class"] = "com.beyondgrader.resourceagent.AgentKt"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
}
kotlinter {
    disabledRules = arrayOf("filename", "enum-entry-name-case")
}
