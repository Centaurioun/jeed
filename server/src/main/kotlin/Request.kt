package edu.illinois.cs.cs125.jeed.server

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.mongodb.client.MongoCollection
import edu.illinois.cs.cs125.jeed.core.CheckstyleFailed
import edu.illinois.cs.cs125.jeed.core.CompilationFailed
import edu.illinois.cs.cs125.jeed.core.ComplexityFailed
import edu.illinois.cs.cs125.jeed.core.ExecutionFailed
import edu.illinois.cs.cs125.jeed.core.Interval
import edu.illinois.cs.cs125.jeed.core.KtLintFailed
import edu.illinois.cs.cs125.jeed.core.SnippetTransformationFailed
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.SourceType
import edu.illinois.cs.cs125.jeed.core.TemplatingFailed
import edu.illinois.cs.cs125.jeed.core.cexecute
import edu.illinois.cs.cs125.jeed.core.checkstyle
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.complexity
import edu.illinois.cs.cs125.jeed.core.defaultChecker
import edu.illinois.cs.cs125.jeed.core.distinguish
import edu.illinois.cs.cs125.jeed.core.execute
import edu.illinois.cs.cs125.jeed.core.fromSnippet
import edu.illinois.cs.cs125.jeed.core.fromTemplates
import edu.illinois.cs.cs125.jeed.core.kompile
import edu.illinois.cs.cs125.jeed.core.ktLint
import edu.illinois.cs.cs125.jeed.core.moshi.CompiledSourceResult
import edu.illinois.cs.cs125.jeed.core.moshi.ExecutionFailedResult
import edu.illinois.cs.cs125.jeed.core.moshi.PermissionAdapter
import edu.illinois.cs.cs125.jeed.core.moshi.SourceTaskResults
import edu.illinois.cs.cs125.jeed.core.moshi.TemplatedSourceResult
import edu.illinois.cs.cs125.jeed.core.server.FlatComplexityResults
import edu.illinois.cs.cs125.jeed.core.server.Task
import edu.illinois.cs.cs125.jeed.core.server.TaskArguments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.apache.http.auth.AuthenticationException
import org.bson.BsonDocument
import org.bson.BsonString
import java.time.Instant

private val mongoScope = CoroutineScope(Dispatchers.IO)

@Suppress("LongParameterList")
class Request(
    passedSource: Map<String, String>?,
    val templates: Map<String, String>?,
    passedSnippet: String?,
    passedTasks: Set<Task>,
    arguments: TaskArguments?,
    @Suppress("MemberVisibilityCanBePrivate") val authToken: String?,
    val label: String,
    val waitForSave: Boolean = false,
    val requireSave: Boolean = true,
    val checkForSnippet: Boolean = false
) {
    val tasks: Set<Task>
    val arguments = arguments ?: TaskArguments()

    var email: String? = null
    var audience: List<String>? = null

    val source: Map<String, String>?
    val snippet: String?
    init {
        var potentialSource = passedSource
        var potentialSnippet = passedSnippet

        val tasksToRun = passedTasks.toMutableSet()

        require(!(potentialSource != null && potentialSnippet != null)) {
            "can't create task with both sources and snippet"
        }
        require(potentialSource != null || potentialSnippet != null) {
            "must provide either sources or a snippet"
        }

        if (potentialSource != null) {
            val fileTypes = Source.filenamesToFileTypes(potentialSource.keys)
            require(fileTypes.size == 1) { "can't compile mixed Java and Kotlin sources" }
            if (tasksToRun.contains(Task.checkstyle)) {
                require(fileTypes[0] == Source.FileType.JAVA) { "can't run checkstyle on Kotlin sources" }
            }
            // Hack to support snippets when we know the language but not whether the content is a snippet or not
            if (potentialSource.keys.size == 1 && checkForSnippet) {
                val fileType = fileTypes.first()
                val singleSource = potentialSource.values.first()
                if (
                    fileType == Source.FileType.JAVA &&
                    singleSource.distinguish("java") == SourceType.JAVA_SNIPPET
                ) {
                    potentialSnippet = singleSource
                    potentialSource = null
                } else if (
                    fileType == Source.FileType.KOTLIN &&
                    singleSource.distinguish("kotlin") == SourceType.KOTLIN_SNIPPET
                ) {
                    potentialSnippet = singleSource
                    potentialSource = null
                }
            }
        }

        if (templates != null) {
            require(potentialSource != null) { "can't use both templates and snippet mode" }
        }
        if (tasksToRun.contains(Task.execute)) {
            require(tasksToRun.contains(Task.compile) || tasksToRun.contains(Task.kompile)) {
                "must compile code before execution"
            }
        }
        require(!(tasksToRun.containsAll(setOf(Task.compile, Task.kompile)))) {
            "can't compile code as both Java and Kotlin"
        }
        require(!(tasksToRun.containsAll(setOf(Task.execute, Task.cexecute)))) {
            "can't both run code in the sandbox and in the container"
        }
        if (potentialSnippet != null) {
            tasksToRun.add(Task.snippet)
        }
        if (templates != null) {
            tasksToRun.add(Task.template)
        }
        tasks = tasksToRun.toSet()
        source = potentialSource
        snippet = potentialSnippet
    }

    fun check(): Request {
        @Suppress("MaxLineLength")
        if (Task.snippet in tasks && Task.checkstyle in tasks && defaultChecker.indentation != null) {
            require(arguments.snippet.indent == defaultChecker.indentation) {
                "snippet indentation must match checkstyle indentation: " +
                    "${arguments.snippet.indent} != ${defaultChecker.indentation}"
            }
        }
        if (Task.execute in tasks) {
            require(arguments.execution.timeout <= configuration[Limits.Execution.timeout]) {
                "job timeout of ${arguments.execution.timeout} too long (> ${configuration[Limits.Execution.timeout]})"
            }
            require(arguments.execution.maxExtraThreads <= configuration[Limits.Execution.maxExtraThreads]) {
                "job maxExtraThreads of ${arguments.execution.maxExtraThreads} is too large " +
                    "(> ${configuration[Limits.Execution.maxExtraThreads]}"
            }
            require(arguments.execution.maxOutputLines <= configuration[Limits.Execution.maxOutputLines]) {
                "job maxOutputLines of ${arguments.execution.maxOutputLines} is too large " +
                    "(> ${configuration[Limits.Execution.maxOutputLines]}"
            }
            val allowedPermissions =
                configuration[Limits.Execution.permissions].map { PermissionAdapter().permissionFromJson(it) }
                    .toSet()
            require(allowedPermissions.containsAll(arguments.execution.permissions)) {
                "job is requesting unavailable permissions: ${arguments.execution.permissions}"
            }

            val blacklistedClasses = configuration[Limits.Execution.ClassLoaderConfiguration.blacklistedClasses]

            require(arguments.execution.classLoaderConfiguration.blacklistedClasses.containsAll(blacklistedClasses)) {
                "job is trying to remove blacklisted classes"
            }
            val whitelistedClasses = configuration[Limits.Execution.ClassLoaderConfiguration.whitelistedClasses]
            require(arguments.execution.classLoaderConfiguration.whitelistedClasses.containsAll(whitelistedClasses)) {
                "job is trying to add whitelisted classes"
            }
            val unsafeExceptions = configuration[Limits.Execution.ClassLoaderConfiguration.unsafeExceptions]
            require(arguments.execution.classLoaderConfiguration.unsafeExceptions.containsAll(unsafeExceptions)) {
                "job is trying to remove unsafe exceptions"
            }
        }
        if (Task.cexecute in tasks) {
            require(arguments.cexecution.timeout <= configuration[Limits.Cexecution.timeout]) {
                "job timeout of ${arguments.cexecution.timeout} too long " +
                    "(> ${configuration[Limits.Cexecution.timeout]})"
            }
        }
        return this
    }

    @Suppress("ComplexMethod", "NestedBlockDepth")
    fun authenticate() {
        @Suppress("EmptyCatchBlock", "TooGenericExceptionCaught")
        try {
            if (googleTokenVerifier != null && authToken != null) {
                googleTokenVerifier?.verify(authToken)?.let {
                    if (configuration[Auth.Google.hostedDomain] != null) {
                        require(it.payload.hostedDomain == configuration[Auth.Google.hostedDomain])
                    }
                    email = it.payload.email
                    audience = it.payload.audienceAsList
                }
            }
        } catch (e: IllegalArgumentException) {
        } catch (e: Exception) {
            logger.warn(e.toString())
        }

        if (email == null && !configuration[Auth.none]) {
            val message = if (authToken == null) {
                "authentication required by authentication token missing"
            } else {
                "authentication failure"
            }
            throw AuthenticationException(message)
        }
    }

    @Suppress("ComplexMethod", "LongMethod")
    suspend fun run(): Response {
        currentStatus.counts.submitted++

        val started = Instant.now()

        val response = Response(this)
        @Suppress("TooGenericExceptionCaught")
        try {
            val actualSource = if (source != null) {
                if (templates == null) {
                    Source(source)
                } else {
                    Source.fromTemplates(source, templates).also {
                        response.completedTasks.add(Task.template)
                        response.completed.template = TemplatedSourceResult(it)
                    }
                }
            } else {
                arguments.snippet.fileType = when {
                    tasks.contains(Task.kompile) -> {
                        Source.FileType.KOTLIN
                    }
                    tasks.contains(Task.compile) -> {
                        Source.FileType.JAVA
                    }
                    else -> {
                        arguments.snippet.fileType
                    }
                }
                Source.fromSnippet(snippet ?: error("should have a snippet"), arguments.snippet).also {
                    response.completedTasks.add(Task.snippet)
                    response.completed.snippet = it
                }
            }

            val compiledSource = when {
                tasks.contains(Task.compile) -> {
                    actualSource.compile(arguments.compilation).also {
                        response.completed.compilation = CompiledSourceResult(it)
                        response.completedTasks.add(Task.compile)
                    }
                }
                tasks.contains(Task.kompile) -> {
                    actualSource.kompile(arguments.kompilation).also {
                        response.completed.kompilation = CompiledSourceResult(it)
                        response.completedTasks.add(Task.kompile)
                    }
                }
                else -> {
                    null
                }
            }

            if (tasks.contains(Task.checkstyle)) {
                check(actualSource.type == Source.FileType.JAVA) { "can't run checkstyle on non-Java sources" }
                response.completed.checkstyle = actualSource.checkstyle(arguments.checkstyle)
                response.completedTasks.add(Task.checkstyle)
            } else if (tasks.contains(Task.ktlint)) {
                check(actualSource.type == Source.FileType.KOTLIN) { "can't run ktlint on non-Kotlin sources" }
                response.completed.ktlint = actualSource.ktLint(arguments.ktlint)
                response.completedTasks.add(Task.ktlint)
            }

            if (tasks.contains(Task.complexity)) {
                check(actualSource.type == Source.FileType.JAVA) { "can't run complexity on non-Java sources" }
                response.completed.complexity = FlatComplexityResults(actualSource.complexity())
                response.completedTasks.add(Task.complexity)
            }

            if (tasks.contains(Task.execute)) {
                check(compiledSource != null) { "should have compiled source before executing" }
                val executionResult = compiledSource.execute(arguments.execution)
                response.completed.execution = SourceTaskResults(actualSource, executionResult, arguments.execution)
                response.completedTasks.add(Task.execute)
            } else if (tasks.contains(Task.cexecute)) {
                check(compiledSource != null) { "should have compiled source before executing" }
                response.completed.cexecution = compiledSource.cexecute(arguments.cexecution)
                response.completedTasks.add(Task.cexecute)
            }
        } catch (templatingFailed: TemplatingFailed) {
            response.failed.template = templatingFailed
            response.failedTasks.add(Task.template)
        } catch (snippetFailed: SnippetTransformationFailed) {
            response.failed.snippet = snippetFailed
            response.failedTasks.add(Task.snippet)
        } catch (compilationFailed: CompilationFailed) {
            if (tasks.contains(Task.compile)) {
                response.failed.compilation = compilationFailed
                response.failedTasks.add(Task.compile)
            } else if (tasks.contains(Task.kompile)) {
                response.failed.kompilation = compilationFailed
                response.failedTasks.add(Task.kompile)
            }
        } catch (checkstyleFailed: CheckstyleFailed) {
            response.failed.checkstyle = checkstyleFailed
            response.failedTasks.add(Task.checkstyle)
        } catch (ktlintFailed: KtLintFailed) {
            response.failed.ktlint = ktlintFailed
            response.failedTasks.add(Task.ktlint)
        } catch (complexityFailed: ComplexityFailed) {
            response.failed.complexity = complexityFailed
            response.failedTasks.add(Task.complexity)
        } catch (executionFailed: ExecutionFailed) {
            if (tasks.contains(Task.execute)) {
                response.failed.execution = ExecutionFailedResult(executionFailed)
                response.failedTasks.add(Task.execute)
            } else if (tasks.contains(Task.cexecute)) {
                response.failed.cexecution = ExecutionFailedResult(executionFailed)
                response.failedTasks.add(Task.cexecute)
            }
        } finally {
            currentStatus.counts.completed++
            response.interval = Interval(started, Instant.now())
        }
        if (mongoCollection != null) {
            val resultSave = mongoScope.async {
                @Suppress("TooGenericExceptionCaught")
                try {
                    mongoCollection?.insertOne(
                        BsonDocument.parse(response.json).also {
                            it.append("receivedSemester", BsonString(configuration[TopLevel.semester]))
                        }
                    )
                    currentStatus.counts.saved++
                } catch (e: Exception) {
                    logger.error("Saving job failed: $e")
                    if (requireSave) {
                        throw(e)
                    } else {
                        null
                    }
                }
            }
            if (waitForSave || requireSave) {
                resultSave.await()
            }
        }
        return response
    }

    companion object {
        var mongoCollection: MongoCollection<BsonDocument>? = null
        var googleTokenVerifier: GoogleIdTokenVerifier? = null
    }
}
