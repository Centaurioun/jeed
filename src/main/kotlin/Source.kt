package edu.illinois.cs.cs125.jeed

import java.io.PrintWriter
import java.io.StringWriter

open class Source(
        val sources: Map<String, String>
) {
    init {
        require(sources.keys.isNotEmpty())
    }
    open fun mapLocation(input: SourceLocation): SourceLocation {
        return input
    }
    companion object
}

data class SourceLocation(
        val source: String?,
        val line: Int,
        val column: Int
) {
    override fun toString(): String {
        return if (source != null) {
            "$source $line:$column"
        } else {
            "(Input) $line:$column"
        }
    }
}
data class Location(val line: Int, val column: Int)
data class SourceRange(
        val source: String?,
        val start: Location,
        val end: Location
)
abstract class SourceError(
        val location: SourceLocation,
        val message: String?
) {
    override fun toString(): String {
        return "$location: $message"
    }
}
abstract class JeepError(val errors: List<SourceError>) : Exception()

fun Exception.getStackTraceAsString(): String {
    val stringWriter = StringWriter()
    val printWriter = PrintWriter(stringWriter)
    this.printStackTrace(printWriter)
    return stringWriter.toString()
}

data class TaskError(val error: Throwable) {
    val stackTrace: String
    init {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        error.printStackTrace(printWriter)
        stackTrace = stringWriter.toString()
    }

    override fun toString(): String {
        return error.toString()
    }
}
