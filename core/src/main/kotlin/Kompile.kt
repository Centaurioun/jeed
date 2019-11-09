package edu.illinois.cs.cs125.jeed.core

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.testFramework.LightVirtualFile
import com.squareup.moshi.JsonClass
import io.github.classgraph.ClassGraph
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.configureExplicitContentRoots
import org.jetbrains.kotlin.codegen.GeneratedClassLoader
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.psi.KtFile
import java.nio.charset.Charset
import java.time.Instant
import java.util.*
import javax.tools.ToolProvider

private val classpath = ClassGraph().classpathFiles.joinToString(separator = ":")

@JsonClass(generateAdapter = true)
data class KompilationArguments(
        @Transient val parentClassLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
        val verbose: Boolean = DEFAULT_VERBOSE,
        val allWarningsAsErrors: Boolean = DEFAULT_ALLWARNINGSASERRORS
) {
    val arguments: K2JVMCompilerArguments = K2JVMCompilerArguments()
    init {
        arguments.classpath = classpath

        arguments.verbose = verbose
        arguments.allWarningsAsErrors = allWarningsAsErrors

        arguments.noStdlib = true
    }
    companion object {
        const val DEFAULT_VERBOSE = false
        const val DEFAULT_ALLWARNINGSASERRORS = false
    }
}

private class JeedMessageCollector(val source: Source, val allWarningsAsErrors: Boolean) : MessageCollector {
    private val messages: MutableList<CompilationMessage> = mutableListOf()

    override fun clear() {
        messages.clear()
    }
    val errors: List<CompilationError>
        get() = messages.filter {
            it.kind == CompilerMessageSeverity.ERROR.presentableName
                    || allWarningsAsErrors && (it.kind == CompilerMessageSeverity.WARNING.presentableName
                        || it.kind == CompilerMessageSeverity.STRONG_WARNING.presentableName)
        }.map {
            CompilationError(it.location, it.message)
        }

    override fun hasErrors(): Boolean {
        return errors.isNotEmpty()
    }

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
        if (severity == CompilerMessageSeverity.LOGGING || severity == CompilerMessageSeverity.INFO) {
            return
        }
        require(location != null) { "location should not be null: $severity" }
        val originalLocation = SourceLocation(location.path, location.line, location.column)
        messages.add(CompilationMessage(severity.presentableName, source.mapLocation(originalLocation), message))
    }
}

@Throws(CompilationFailed::class)
private fun kompile(
        kompilationArguments: KompilationArguments, source: Source
): CompiledSource {
    require(source.type == Source.FileType.KOTLIN) { "Kotlin compiler needs Kotlin sources" }

    val started = Instant.now()

    val rootDisposable = Disposer.newDisposable()
    val messageCollector = JeedMessageCollector(source, kompilationArguments.arguments.allWarningsAsErrors)
    val configuration = CompilerConfiguration().apply {
        put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        put(CommonConfigurationKeys.MODULE_NAME, JvmProtoBufUtil.DEFAULT_MODULE_NAME)
        configureExplicitContentRoots(kompilationArguments.arguments)
    }

    val environment = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)

    val psiFileFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl
    val psiFiles = source.sources.map { (name, contents) ->
        val virtualFile = LightVirtualFile(name, KotlinLanguage.INSTANCE, contents)
        psiFileFactory.trySetupPsiForFile(virtualFile, KotlinLanguage.INSTANCE, true, false) as KtFile?
                ?: error("couldn't parse source to psiFile")
    }.toMutableList()

    environment::class.java.getDeclaredField("sourceFiles").also { field ->
        field.isAccessible = true
        field.set(environment, psiFiles)
    }

    val state = KotlinToJVMBytecodeCompiler.analyzeAndGenerate(environment)
    if (messageCollector.errors.isNotEmpty()) {
        throw CompilationFailed(messageCollector.errors)
    }
    check(state != null) { "compilation should have succeeded" }

    val results = Results()
    val fileManager = JeedFileManager(ToolProvider.getSystemJavaCompiler().getStandardFileManager(
            results, Locale.US, Charset.forName("UTF-8")
    ), GeneratedClassLoader(state.factory, kompilationArguments.parentClassLoader))
    require(results.diagnostics.size == 0) { "fileManager generated errors during Kotlin compilation" }

    val classLoader = JeedClassLoader(fileManager, kompilationArguments.parentClassLoader)

    return CompiledSource(source, listOf(), Interval(started, Instant.now()), classLoader, fileManager)
}

fun Source.kompile(kompilationArguments: KompilationArguments = KompilationArguments()): CompiledSource {
    return kompile(kompilationArguments, this)
}
