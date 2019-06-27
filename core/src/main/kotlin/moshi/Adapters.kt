package edu.illinois.cs.cs125.jeed.core.moshi

import edu.illinois.cs.cs125.jeed.core.*
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.security.Permission
import java.time.Instant

@JvmField
val Adapters = setOf(
        CompilationFailedAdapter(),
        CompiledSourceAdapter(),
        SnippetParseErrorAdapter(),
        SnippetParsingFailedAdapter(),
        SnippetValidationFailedAdapter(),
        PermissionAdapter(),
        ThrowableAdapter(),
        InstantAdapter()
)

data class CompilationFailedJson(val errors: List<CompilationFailed.CompilationError>)
class CompilationFailedAdapter {
    @FromJson
    fun compilationFailedFromJson(compilationFailedJson: CompilationFailedJson): CompilationFailed {
        return CompilationFailed(compilationFailedJson.errors)
    }
    @Suppress("UNCHECKED_CAST")
    @ToJson
    fun compilationFailedToJson(compilationFailed: CompilationFailed): CompilationFailedJson {
        return CompilationFailedJson(compilationFailed.errors as List<CompilationFailed.CompilationError>)
    }
}
data class CompiledSourceJson(val messages: List<CompiledSource.CompilationMessage>)
class CompiledSourceAdapter {
    @Throws(Exception::class)
    @Suppress("UNUSED_PARAMETER")
    @FromJson
    fun compiledSourceFromJson(unused: CompiledSourceJson): CompiledSource {
        throw Exception("Can't convert JSON to CompiledSourceAdapter")
    }
    @ToJson
    fun compiledSourceToJson(compiledSource: CompiledSource): CompiledSourceJson {
        return CompiledSourceJson(compiledSource.messages)
    }
}
data class SnippetParseErrorJson(val line: Int, val column: Int, val message: String)
class SnippetParseErrorAdapter {
    @FromJson fun snippetParseErrorFromJson(snippetParseErrorJson: SnippetParseErrorJson): SnippetParseError {
        return SnippetParseError(snippetParseErrorJson.line, snippetParseErrorJson.column, snippetParseErrorJson.message)
    }
    @ToJson fun snippetParseErrorToJson(snippetParseError: SnippetParseError): SnippetParseErrorJson {
        return SnippetParseErrorJson(snippetParseError.location.line, snippetParseError.location.column, snippetParseError.message)
    }
}
data class SnippetParsingFailedJson(val errors: List<SnippetParseError>)
class SnippetParsingFailedAdapter {
    @FromJson fun snippetParsingFailedFromJson(snippetParsingFailedJson: SnippetParsingFailedJson): SnippetParsingFailed {
        return SnippetParsingFailed(snippetParsingFailedJson.errors)
    }
    @Suppress("UNCHECKED_CAST")
    @ToJson fun snippetParsingFailedToJson(snippetParsingFailed: SnippetParsingFailed): SnippetParsingFailedJson {
        return SnippetParsingFailedJson(snippetParsingFailed.errors as List<SnippetParseError>)
    }
}
data class SnippetValidationFailedJson(val errors: List<SnippetValidationError>)
class SnippetValidationFailedAdapter {
    @FromJson fun snippetValidationFailedFromJson(snippetValidationFailedJson: SnippetValidationFailedJson): SnippetValidationFailed {
        return SnippetValidationFailed(snippetValidationFailedJson.errors)
    }
    @Suppress("UNCHECKED_CAST")
    @ToJson fun snippetValidationFailedToJson(snippetValidationFailed: SnippetValidationFailed): SnippetValidationFailedJson {
        return SnippetValidationFailedJson(snippetValidationFailed.errors as List<SnippetValidationError>)
    }
}
data class PermissionJson(val type: String, val name: String, val actions: String?)
class PermissionAdapter {
    @FromJson fun permissionFromJson(permissionJson: PermissionJson): Permission {
        val klass = Class.forName("java.security.${permissionJson.type}")
        val constructor = klass.getConstructor(String::class.java, String::class.java)
        return constructor.newInstance(permissionJson.name, permissionJson.actions) as Permission
    }
    @ToJson fun permissionToJson(permission: Permission): PermissionJson {
        return PermissionJson(permission.javaClass.name.split(".").last(), permission.name, permission.actions)
    }
}
data class ThrowableJson(val klass: String, val message: String?)
class ThrowableAdapter {
    @Throws(Exception::class)
    @Suppress("UNUSED_PARAMETER")
    @FromJson
    fun throwableFromJson(unused: ThrowableJson): Throwable {
        throw Exception("Can't convert JSON to Throwable")
    }
    @ToJson fun throwableToJson(throwable: Throwable): ThrowableJson {
        return ThrowableJson(throwable::class.java.typeName, throwable.message)
    }
}
class InstantAdapter {
    @FromJson fun instantFromJson(timestamp: String): Instant {
        return Instant.parse(timestamp)
    }
    @ToJson fun instantToJson(instant: Instant): String {
        return instant.toString()
    }
}
