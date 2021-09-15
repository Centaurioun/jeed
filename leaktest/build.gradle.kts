plugins {
    kotlin("jvm")
    application
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
}
dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("ch.qos.logback:logback-classic:1.2.5")
    implementation("io.github.microutils:kotlin-logging:2.0.11")
}
application {
    @Suppress("DEPRECATION")
    mainClassName = "edu.illinois.cs.cs125.jeed.leaktest.MainKt"
}
tasks {
    "run"(JavaExec::class) {
        jvmArgs(
            "-ea",
            "-Xms64m",
            "-Xmx128m",
            "--enable-preview",
            "--illegal-access=permit"
        )
    }
}
