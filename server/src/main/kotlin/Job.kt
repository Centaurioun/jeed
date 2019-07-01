package edu.illinois.cs.cs125.jeed.server

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import edu.illinois.cs.cs125.jeed.core.*

class Job(
        val source: Source?,
        val snippet: String?,
        passedTasks: Set<Task>,
        arguments: TaskArguments?
) {
    val tasks: Set<Task>
    val arguments = arguments ?: TaskArguments()

    init {
        require(!(source != null && snippet != null)) { "can't create task with both source and snippet" }
        val tasksToRun = passedTasks.toMutableSet()
        if (tasksToRun.contains(Task.execute)) { tasksToRun.add(Task.compile) }
        if (snippet != null) { tasksToRun.add(Task.snippet) }
        tasks = tasksToRun.toSet()
    }
    suspend fun run(): Result {
        val result = Result(this)
        val actualSource = if (source != null) { source } else {
            try {
                result.completed.snippet = Source.transformSnippet(snippet ?: assert { "should have a snippet" })
                result.completed.snippet
            } catch (snippetFailed: SnippetTransformationFailed) {
                result.failed.snippet = snippetFailed
                return result
            }
        } ?: check { "should have a source" }

        val compiledSource: CompiledSource? = if (tasks.contains(Task.compile)) {
            try {
                result.completed.compilation = actualSource.compile(arguments.compilation)
                result.completed.compilation
            } catch (compilationFailed: CompilationFailed) {
                result.failed.compilation = compilationFailed
                null
            }
        } else { null }

        if (tasks.contains(Task.execute) && compiledSource != null) {
            result.completed.execution = compiledSource.execute(arguments.execution)
        }

        return result
    }

    class JobJson(
            val source: Source?,
            val snippet: String?,
            val tasks: Set<Task>,
            val arguments: TaskArguments?
    )
    class JobAdapter {
        @FromJson
        fun jobFromJson(jobJson: JobJson): Job {
            assert(!(jobJson.source != null && jobJson.snippet != null)) { "can't set both snippet and sources" }
            return Job(jobJson.source, jobJson.snippet, jobJson.tasks, jobJson.arguments)
        }
        @ToJson
        fun jobToJson(job: Job): JobJson {
            assert(!(job.source != null && job.snippet != null)) { "can't set both snippet and sources" }
            return JobJson(job.source, job.snippet, job.tasks, job.arguments)
        }
    }
}

@Suppress("NAMING")
enum class Task(val task: String) {
    snippet("snippet"),
    compile("compile"),
    execute("execute")
}
class TaskArguments(
        val compilation: CompilationArguments = CompilationArguments(),
        val execution: SourceExecutionArguments = SourceExecutionArguments()
)

class Result(job: Job) {
    val tasks = job.tasks
    val arguments = job.arguments
    val completed: CompletedTasks = CompletedTasks()
    val failed: FailedTasks = FailedTasks()

    data class ResultJson(
            val tasks: Set<Task>,
            val arguments: TaskArguments,
            val completed: CompletedTasks,
            val failed: FailedTasks
    )
    class ResultAdapter {
        @Throws(Exception::class)
        @Suppress("UNUSED_PARAMETER")
        @FromJson
        fun resultFromJson(resultJson: ResultJson): Result {
            throw Exception("Can't convert JSON to Result")
        }
        @ToJson
        fun resultToJson(result: Result): ResultJson {
            return ResultJson(result.tasks, result.arguments, result.completed, result.failed)
        }
    }

}
class CompletedTasks(
        var snippet: Snippet? = null,
        var compilation: CompiledSource? = null,
        var execution: Sandbox.TaskResults<out Any?>? = null
)
class FailedTasks(
        var snippet: SnippetTransformationFailed? = null,
        var compilation: CompilationFailed? = null
)

@JvmField
val Adapters = setOf(
        Job.JobAdapter(),
        Result.ResultAdapter()
)
