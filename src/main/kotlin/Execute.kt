package edu.illinois.cs.cs125.jeed

import mu.KotlinLogging
import java.io.OutputStream
import java.io.PrintStream
import java.lang.StringBuilder
import java.lang.reflect.Modifier
import java.security.Permissions
import java.time.Duration
import java.time.Instant
import kotlin.concurrent.withLock
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock

@Suppress("UNUSED")
private val logger = KotlinLogging.logger {}

data class ExecutionPermission(
        val type: String,
        val name: String
)
fun List<ExecutionPermission>.toPermission(): Permissions {
    val permissions = Permissions()
    return permissions
}
data class ExecutionArguments(
        val className: String = "Main",
        val method: String = "main()",
        val timeout: Long = 100L,
        val permissions: List<ExecutionPermission> = listOf()
)
class ExecutionResult(
        val completed: Boolean,
        val timedOut: Boolean,
        val permissionDenied: Boolean,
        val stdoutLines: List<OutputLine> = arrayListOf(),
        val stderrLines: List<OutputLine> = arrayListOf()
        ) {
    fun stdout(): String {
        return stdoutLines.joinToString(separator = "\n") { it.line }
    }
    fun stderr(): String {
        return stderrLines.joinToString(separator = "\n") { it.line }
    }
    fun output(): String {
        return stdoutLines.union(stderrLines)
                .sortedBy { it.timestamp }
                .joinToString(separator = "\n") { it.line }
    }
}

class ExecutionException(message: String) : Exception(message)

val outputLock = ReentrantLock()

fun CompiledSource.execute(
        executionArguments: ExecutionArguments = ExecutionArguments()
): ExecutionResult {
    val klass = classLoader.loadClass(executionArguments.className)
            ?: throw ExecutionException("Could not load ${executionArguments.className}")
    val method = klass.declaredMethods.find { method ->
        val fullName = method.name + method.parameterTypes.joinToString(prefix = "(", separator = ", ", postfix = ")") { parameter ->
            parameter.name
        }
        fullName == executionArguments.method && Modifier.isStatic(method.modifiers) && Modifier.isPublic(method.modifiers)
    } ?: throw ExecutionException("Cannot locate public static method with signature ${executionArguments.method} in ${executionArguments.className}")

    val futureTask = FutureTask { method.invoke(null) }
    val thread = Thread(futureTask)

    outputLock.withLock {
        var timedOut = false
        var permissionDenied: Boolean
        val stdoutStream = ConsoleOutputStream()
        val stderrStream = ConsoleOutputStream()

        val originalStdout = System.out
        val originalStderr = System.err
        System.setOut(PrintStream(stdoutStream))
        System.setErr(PrintStream(stderrStream))

        val completed = try {
            Sandbox.confine(classLoader, executionArguments.permissions.toPermission())
            Sandbox.log()
            thread.start()
            futureTask.get(executionArguments.timeout, TimeUnit.MILLISECONDS)
            true
        } catch (e: TimeoutException) {
            futureTask.cancel(true)
            @Suppress("DEPRECATION")
            thread.stop()
            timedOut = true
            false
        } catch (e: Throwable) {
            false
        } finally {
            val permissionRequests = Sandbox.retrieve()
            permissionDenied = permissionRequests.any { !it.granted }

            System.out.flush()
            System.err.flush()
            System.setOut(originalStdout)
            System.setErr(originalStderr)
        }

        return ExecutionResult(
                completed = completed && !permissionDenied,
                timedOut = timedOut,
                permissionDenied = permissionDenied,
                stdoutLines = stdoutStream.lines,
                stderrLines = stderrStream.lines
        )
    }
}

data class OutputLine (
        val line: String,
        val timestamp: Instant,
        val delta: Duration
)
private class ConsoleOutputStream : OutputStream() {
    val started: Instant = Instant.now()
    val lines = mutableListOf<OutputLine>()

    var currentLineStarted: Instant = Instant.now()
    var currentLine = StringBuilder()

    override fun write(int: Int) {
        val char = int.toChar()
        if (char == '\n') {
            lines.add(OutputLine(
                    currentLine.toString(),
                    currentLineStarted,
                    Duration.between(started, currentLineStarted
                    )))
            currentLine = StringBuilder()
            currentLineStarted = Instant.now()
        } else {
            currentLine.append(char)
        }
    }
}
