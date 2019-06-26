plugins {
    kotlin("jvm")
    kotlin("kapt")
    antlr
    java
    maven
}
tasks.test {
    useJUnitPlatform()
    systemProperties["logback.configurationFile"] = File(projectDir, "src/test/resources/logback-test.xml").absolutePath
}
dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0-M1")

    antlr("org.antlr:antlr4:4.7.2")

    implementation("com.puppycrawl.tools:checkstyle:8.22")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.3.31")

    implementation("com.squareup.moshi:moshi:1.8.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.8.0")

    implementation("org.slf4j:slf4j-api:1.7.26")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("io.github.microutils:kotlin-logging:1.6.26")
    implementation("org.ow2.asm:asm:7.1")
    implementation("org.ow2.asm:asm-util:7.1")

    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
}
tasks.generateGrammarSource {
    outputDirectory = File(projectDir, "src/main/java/edu/illinois/cs/cs125/jeed/core/antlr")
    arguments.addAll(listOf("-visitor", "-package", "edu.illinois.cs.cs125.jeed.core.antlr", "-Xexact-output-dir"))
}
tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}
