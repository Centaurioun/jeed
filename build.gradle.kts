import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31" apply false
    kotlin("kapt") version "1.5.31" apply false
    id("org.jmailen.kotlinter") version "3.6.0" apply false
    id("com.github.ben-manes.versions") version "0.39.0"
    id("io.gitlab.arturbosch.detekt") version "1.18.1"
}
allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.google.com/")
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    }
}
subprojects {
    group = "com.github.cs125-illinois.jeed"
    version = "2021.10.2"
    tasks.withType<Test> {
        useJUnitPlatform()
        enableAssertions = true
        // Fix encoding bug on Windows
        jvmArgs("-Dfile.encoding=UTF-8", "--illegal-access=permit")
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_16.toString()
        }
    }
}
tasks.dependencyUpdates {
    fun String.isNonStable() = !(
        listOf("RELEASE", "FINAL", "GA").any { toUpperCase().contains(it) }
            || "^[0-9,.v-]+(-r)?$".toRegex().matches(this)
        )
    rejectVersionIf { candidate.version.isNonStable() }
    gradleReleaseChannel = "current"
}
detekt {
    buildUponDefaultConfig = true
}
tasks.register("check") {
    dependsOn("detekt")
}
