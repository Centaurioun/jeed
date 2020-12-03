package edu.illinois.cs.cs125.jeed.core

import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.jeed.core.antlr.JavaLexer
import edu.illinois.cs.cs125.jeed.core.antlr.KnippetLexer
import edu.illinois.cs.cs125.jeed.core.antlr.KnippetParser
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinLexer
import edu.illinois.cs.cs125.jeed.core.antlr.SnippetLexer
import edu.illinois.cs.cs125.jeed.core.antlr.SnippetParser
import edu.illinois.cs.cs125.jeed.core.antlr.SnippetParserBaseVisitor
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

const val SNIPPET_SOURCE = ""

@Suppress("LongParameterList")
class Snippet(
    sources: Sources,
    val originalSource: String,
    val rewrittenSource: String,
    val snippetRange: SourceRange,
    val wrappedClassName: String,
    val looseCodeMethodName: String,
    val fileType: FileType,
    @Transient private val remappedLineMapping: Map<Int, RemappedLine> = mapOf()
) : Source(
    sources,
    {
        require(sources.keys.size == 1) { "snippets should only provide a single source file" }
        require(sources.keys.first() == "") { "snippets should use a blank string as their filename" }
        fileType
    },
    { mapLocation(it, remappedLineMapping) }
) {
    data class RemappedLine(val sourceLineNumber: Int, val rewrittenLineNumber: Int, val addedIndentation: Int = 0)

    fun originalSourceFromMap(): String {
        val lines = rewrittenSource.lines()
        return remappedLineMapping.values.sortedBy { it.sourceLineNumber }.joinToString(separator = "\n") {
            val currentLine = lines[it.rewrittenLineNumber - 1]
            assert(it.addedIndentation <= currentLine.length) { "${it.addedIndentation} v. ${currentLine.length}" }
            currentLine.substring(it.addedIndentation)
        }
    }

    companion object {
        fun mapLocation(input: SourceLocation, remappedLineMapping: Map<Int, RemappedLine>): SourceLocation {
            check(input.source == SNIPPET_SOURCE) { "Incorrect input source: ${input.source}" }
            val remappedLineInfo = remappedLineMapping[input.line]
                ?: throw SourceMappingException(
                    "can't remap line ${input.line}: ${
                    remappedLineMapping.values.joinToString(
                        separator = ","
                    )
                    }"
                )
            return SourceLocation(
                SNIPPET_SOURCE,
                remappedLineInfo.sourceLineNumber,
                input.column - remappedLineInfo.addedIndentation
            )
        }
    }
}

class SnippetTransformationError(
    line: Int,
    column: Int,
    message: String
) : AlwaysLocatedSourceError(SourceLocation(SNIPPET_SOURCE, line, column), message) {
    constructor(location: SourceLocation, message: String) : this(location.line, location.column, message)
}

class SnippetTransformationFailed(errors: List<SnippetTransformationError>) : AlwaysLocatedJeedError(errors)

class SnippetErrorListener(
    private val sourceLines: List<Int>,
    private val decrement: Boolean = true
) : BaseErrorListener() {
    private val errors = mutableListOf<SnippetTransformationError>()
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?
    ) {
        // Decrement line number by 1 to account for added braces
        var actualLine = if (decrement) {
            line - 1
        } else {
            line
        }
        var actualCharPositionInLine = charPositionInLine
        var actualMsg = msg

        // HACK to repair broken error message at end of input
        if (actualLine - 1 == sourceLines.size && actualCharPositionInLine == 0 && msg == "missing ';' at '}'") {
            actualLine -= 1
            actualCharPositionInLine = sourceLines[actualLine - 1] + 1
            actualMsg = "missing ';'"
        }

        errors.add(SnippetTransformationError(actualLine, actualCharPositionInLine, actualMsg))
    }

    fun check() {
        if (errors.size > 0) {
            throw SnippetTransformationFailed(errors)
        }
    }
}

@JsonClass(generateAdapter = true)
data class SnippetArguments(
    val indent: Int = 4,
    var fileType: Source.FileType = Source.FileType.JAVA
)

@Suppress("LongMethod", "ComplexMethod")
@Throws(SnippetTransformationFailed::class)
fun Source.Companion.fromSnippet(
    originalSource: String,
    snippetArguments: SnippetArguments = SnippetArguments()
): Snippet {
    require(originalSource.isNotEmpty())
    return when (snippetArguments.fileType) {
        Source.FileType.JAVA -> sourceFromJavaSnippet(originalSource, snippetArguments)
        Source.FileType.KOTLIN -> sourceFromKotlinSnippet(originalSource, snippetArguments)
    }
}

@Suppress("LongMethod", "ComplexMethod", "ThrowsCount")
private fun sourceFromKotlinSnippet(originalSource: String, snippetArguments: SnippetArguments): Snippet {
    val sourceLines = originalSource.lines()
    val errorListener = SnippetErrorListener(sourceLines.map { it.trim().length }, false)

    val parseTree = KnippetLexer(CharStreams.fromString(originalSource + "\n")).let {
        it.removeErrorListeners()
        it.addErrorListener(errorListener)
        CommonTokenStream(it)
    }.also {
        errorListener.check()
    }.let {
        KnippetParser(it)
    }.let {
        it.removeErrorListeners()
        it.addErrorListener(errorListener)
        it.kotlinFile()
    }.also {
        errorListener.check()
    }

    val snippetRange = SourceRange(
        SNIPPET_SOURCE,
        Location(parseTree.start.line, 0),
        Location(parseTree.stop.line - 1, 0)
    )

    val rewrittenSourceLines: MutableList<String> = mutableListOf()
    var currentOutputLineNumber = 1
    val remappedLineMapping = hashMapOf<Int, Snippet.RemappedLine>()

    parseTree.preamble()?.packageHeader()?.let {
        if (it.identifier() != null) {
            throw SnippetTransformationFailed(
                listOf(
                    SnippetTransformationError(
                        SourceLocation(SNIPPET_SOURCE, it.start.line, it.start.charPositionInLine),
                        "package declarations not allowed in snippets"
                    )
                )
            )
        }
    }

    val preambleStart = parseTree.preamble()?.start?.line ?: 0
    val preambleStop = parseTree.preamble()?.stop?.line?.inc() ?: 0
    for (lineNumber in preambleStart until preambleStop) {
        rewrittenSourceLines.add(sourceLines[lineNumber - 1].trimEnd())
        remappedLineMapping[currentOutputLineNumber] = Snippet.RemappedLine(lineNumber, currentOutputLineNumber)
        currentOutputLineNumber++
    }

    """class MainKt {
${" ".repeat(snippetArguments.indent)}companion object {
${" ".repeat(snippetArguments.indent * 2)}@JvmStatic fun main() {""".lines().let {
        rewrittenSourceLines.addAll(it)
        currentOutputLineNumber += it.size
    }

    val topLevelStart = parseTree.topLevelObject()?.first()?.start?.line ?: 0
    val topLevelEnd = parseTree.topLevelObject()?.last()?.stop?.line?.inc() ?: 0
    @Suppress("MagicNumber")
    for (lineNumber in topLevelStart until topLevelEnd) {
        rewrittenSourceLines.add(" ".repeat(snippetArguments.indent * 3) + sourceLines[lineNumber - 1].trimEnd())
        remappedLineMapping[currentOutputLineNumber] =
            Snippet.RemappedLine(lineNumber, currentOutputLineNumber, snippetArguments.indent * 3)
        currentOutputLineNumber++
    }

    rewrittenSourceLines.addAll(
        """${" ".repeat(snippetArguments.indent * 2)}}
${" ".repeat(snippetArguments.indent)}}
}
""".lines()
    )

    val looseLines: MutableList<String> = mutableListOf()
    val looseCodeMapping: MutableMap<Int, Int> = mutableMapOf()

    parseTree.topLevelObject()?.map { it.blockLevelExpression() }?.filterNotNull()?.forEach {
        val looseStart = it.start?.line ?: 0
        val looseEnd = it.stop?.line ?: 0
        for (lineNumber in looseStart..looseEnd) {
            looseCodeMapping[looseLines.size + 1] = lineNumber
            looseLines.add(sourceLines[lineNumber - 1])
        }
    }

    KotlinLexer(CharStreams.fromString(looseLines.joinToString(separator = "\n"))).let {
        it.removeErrorListeners()
        CommonTokenStream(it)
    }.also {
        it.fill()
    }.tokens.filter {
        it.type == KotlinLexer.RETURN
    }.map {
        SnippetTransformationError(
            SourceLocation(
                SNIPPET_SOURCE,
                looseCodeMapping[it.line] ?: require { "Missing loose code mapping " },
                it.charPositionInLine
            ),
            "return statements not allowed at top level in snippets"
        )
    }.let {
        if (it.isNotEmpty()) {
            throw SnippetTransformationFailed(it)
        }
    }

    parseTree.topLevelObject()?.map { it.classDeclaration() }?.find {
        it?.simpleIdentifier()?.text == "MainKt"
    }?.let {
        throw SnippetTransformationFailed(
            listOf(
                SnippetTransformationError(
                    SourceLocation(
                        SNIPPET_SOURCE,
                        it.start.line,
                        it.start.charPositionInLine
                    ),
                    "A class named MainKt cannot be declared at the top level in a snippet"
                )
            )
        )
    }

    val rewrittenSource = rewrittenSourceLines.joinToString(separator = "\n")
    return Snippet(
        Sources(hashMapOf(SNIPPET_SOURCE to rewrittenSource)),
        originalSource,
        rewrittenSource,
        snippetRange,
        "MainKt",
        "main()",
        Source.FileType.KOTLIN,
        remappedLineMapping
    )
}

private val JAVA_VISIBILITY_PATTERN =
    """^\s*(public|private|protected)""".toRegex()

@Suppress("LongMethod", "ComplexMethod")
private fun sourceFromJavaSnippet(originalSource: String, snippetArguments: SnippetArguments): Snippet {
    val sourceLines = originalSource.lines().map { it.trim().length }
    val errorListener = SnippetErrorListener(sourceLines)

    val parseTree = SnippetLexer(CharStreams.fromString("{\n$originalSource\n}")).let {
        it.removeErrorListeners()
        it.addErrorListener(errorListener)
        CommonTokenStream(it)
    }.also {
        errorListener.check()
    }.let {
        SnippetParser(it)
    }.let {
        it.removeErrorListeners()
        it.addErrorListener(errorListener)
        it.snippet()
    }.also {
        errorListener.check()
    }

    lateinit var snippetRange: SourceRange
    val contentMapping = mutableMapOf<Int, String>()
    val classNames = mutableSetOf<String>()
    val methodNames = mutableSetOf<String>()

    val visitorResults = object : SnippetParserBaseVisitor<Unit>() {
        val errors = mutableListOf<SnippetTransformationError>()
        var sawNonImport = false
        var statementDepth = 0

        fun markAs(start: Int, stop: Int, type: String) {
            check(statementDepth == 0) { "Shouldn't mark inside statement" }

            if (type != "import") {
                sawNonImport = true
            } else if (type == "import" && sawNonImport) {
                errors.add(
                    SnippetTransformationError(
                        start - 1,
                        0,
                        "import statements must be at the top of the snippet"
                    )
                )
            }

            for (lineNumber in start - 1 until stop) {
                check(!contentMapping.containsKey(lineNumber)) { "line $lineNumber already marked" }
                contentMapping[lineNumber] = type
            }
        }

        override fun visitPackageDeclaration(context: SnippetParser.PackageDeclarationContext) {
            errors.add(
                SnippetTransformationError(
                    context.start.line - 1,
                    context.start.charPositionInLine,
                    "Snippets may not contain package declarations"
                )
            )
        }

        // Always part of loose code
        override fun visitLocalVariableDeclaration(ctx: SnippetParser.LocalVariableDeclarationContext?) {
            return
        }

        override fun visitLambdaExpression(ctx: SnippetParser.LambdaExpressionContext?) {
            return
        }

        // Always part of loose code
        override fun visitStatement(context: SnippetParser.StatementContext) {
            context.RETURN()?.also {
                errors.add(
                    SnippetTransformationError(
                        context.start.line - 1,
                        context.start.charPositionInLine,
                        "Snippets may not contain top-level return statements"
                    )
                )
            }
            check(statementDepth >= 0) { "Invalid statement depth" }
            statementDepth++
            super.visitStatement(context)
            statementDepth--
            check(statementDepth >= 0) { "Invalid statement depth" }
        }

        override fun visitClassDeclaration(context: SnippetParser.ClassDeclarationContext) {
            if (statementDepth > 0) {
                return
            }
            val parent = context.parent as SnippetParser.LocalTypeDeclarationContext
            markAs(parent.start.line, parent.stop.line, "class")
            val className = context.IDENTIFIER().text
            classNames.add(className)
        }

        override fun visitInterfaceDeclaration(context: SnippetParser.InterfaceDeclarationContext) {
            if (statementDepth > 0) {
                errors.add(
                    SnippetTransformationError(
                        context.start.line - 1,
                        context.start.charPositionInLine,
                        "Interface declarations must be at the top level"
                    )
                )
                return
            }
            val parent = context.parent as SnippetParser.LocalTypeDeclarationContext
            markAs(parent.start.line, parent.stop.line, "class")
            val className = context.IDENTIFIER().text
            classNames.add(className)
        }

        override fun visitRecordDeclaration(context: SnippetParser.RecordDeclarationContext) {
            if (statementDepth > 0) {
                return
            }
            val parent = context.parent as SnippetParser.LocalTypeDeclarationContext
            markAs(parent.start.line, parent.stop.line, "record")
            val className = context.IDENTIFIER().text
            classNames.add(className)
        }

        override fun visitMethodDeclaration(context: SnippetParser.MethodDeclarationContext) {
            if (statementDepth > 0) {
                return
            }
            markAs(context.start.line, context.stop.line, "method")
            contentMapping[context.start.line - 1] = "method:start"
            val methodName = context.IDENTIFIER().text
            methodNames.add(methodName)
        }

        override fun visitGenericMethodDeclaration(context: SnippetParser.GenericMethodDeclarationContext) {
            if (statementDepth > 0) {
                return
            }
            markAs(context.start.line, context.stop.line, "method")
            contentMapping[context.start.line - 1] = "method:start"
            val methodName = context.methodDeclaration().IDENTIFIER().text
            methodNames.add(methodName)
        }

        override fun visitImportDeclaration(context: SnippetParser.ImportDeclarationContext) {
            if (statementDepth > 0) {
                errors.add(
                    SnippetTransformationError(
                        context.start.line - 1,
                        context.start.charPositionInLine,
                        "Imports must be at the top level"
                    )
                )
                return
            }
            markAs(context.start.line, context.stop.line, "import")
        }

        override fun visitSnippet(ctx: SnippetParser.SnippetContext) {
            snippetRange = SourceRange(
                SNIPPET_SOURCE,
                Location(1, 0),
                Location(ctx.stop.line - 2, 0)
            )
            ctx.children.forEach {
                super.visit(it)
            }
        }
    }.also { it.visit(parseTree) }

    if (visitorResults.errors.isNotEmpty()) {
        throw SnippetTransformationFailed(visitorResults.errors)
    }

    val snippetClassName = generateName("Main", classNames)
    val snippetMainMethodName = generateName("main", methodNames)

    var currentOutputLineNumber = 1
    val remappedLineMapping = hashMapOf<Int, Snippet.RemappedLine>()

    val importStatements = mutableListOf<String>()
    originalSource.lines().forEachIndexed { i, line ->
        val lineNumber = i + 1
        if (contentMapping[lineNumber] == "import") {
            importStatements.add(line)
            assert(!remappedLineMapping.containsKey(currentOutputLineNumber))
            remappedLineMapping[currentOutputLineNumber] = Snippet.RemappedLine(lineNumber, currentOutputLineNumber)
            currentOutputLineNumber++
        }
    }

    val classDeclarations = mutableListOf<String>()
    originalSource.lines().forEachIndexed { i, line ->
        val lineNumber = i + 1
        if (contentMapping[lineNumber] == "class" || contentMapping[lineNumber] == "record") {
            classDeclarations.add(line)
            assert(!remappedLineMapping.containsKey(currentOutputLineNumber))
            remappedLineMapping[currentOutputLineNumber] = Snippet.RemappedLine(lineNumber, currentOutputLineNumber)
            currentOutputLineNumber++
        }
    }

    // Adding public class $snippetClassName
    currentOutputLineNumber++

    val methodDeclarations = mutableListOf<String>()
    originalSource.lines().forEachIndexed { i, line ->
        val lineNumber = i + 1
        if (contentMapping[lineNumber]?.startsWith(("method")) == true) {
            val (actualLine, extraIndentation) =
                if ((contentMapping[lineNumber] == "method:start") && !line.contains("""\bstatic\b""".toRegex())) {
                    val matchVisibilityModifier = JAVA_VISIBILITY_PATTERN.find(line)
                    if (matchVisibilityModifier != null) {
                        val rewrittenLine = line.replace(JAVA_VISIBILITY_PATTERN, "").let {
                            "${matchVisibilityModifier.value} static$it"
                        }
                        Pair(rewrittenLine, "static ".length)
                    } else {
                        Pair("static $line", "static ".length)
                    }
                } else {
                    Pair(line, 0)
                }
            methodDeclarations.add(" ".repeat(snippetArguments.indent) + actualLine)
            assert(!remappedLineMapping.containsKey(currentOutputLineNumber))
            remappedLineMapping[currentOutputLineNumber] =
                Snippet.RemappedLine(
                    lineNumber,
                    currentOutputLineNumber,
                    snippetArguments.indent + extraIndentation
                )
            currentOutputLineNumber++
        }
    }

    // Adding public static void $snippetMainMethodName()
    @Suppress("UNUSED_VARIABLE") val looseCodeStart = currentOutputLineNumber
    currentOutputLineNumber++

    val looseCode = mutableListOf<String>()
    originalSource.lines().forEachIndexed { i, line ->
        val lineNumber = i + 1
        if (!contentMapping.containsKey(lineNumber)) {
            looseCode.add(" ".repeat(snippetArguments.indent * 2) + line)
            assert(!remappedLineMapping.containsKey(currentOutputLineNumber))
            remappedLineMapping[currentOutputLineNumber] =
                Snippet.RemappedLine(lineNumber, currentOutputLineNumber, snippetArguments.indent * 2)
            currentOutputLineNumber++
        }
    }

    assert(originalSource.lines().size == remappedLineMapping.keys.size)

    var rewrittenSource = ""
    if (importStatements.size > 0) {
        rewrittenSource += importStatements.joinToString(separator = "\n", postfix = "\n")
    }
    if (classDeclarations.size > 0) {
        rewrittenSource += classDeclarations.joinToString(separator = "\n", postfix = "\n")
    }
    rewrittenSource += "public class $snippetClassName {\n"
    if (methodDeclarations.size > 0) {
        rewrittenSource += methodDeclarations.joinToString(separator = "\n", postfix = "\n")
    }
    rewrittenSource += """${
    " "
        .repeat(snippetArguments.indent)
    }public static void $snippetMainMethodName() throws Exception {""" + "\n"
    if (looseCode.size > 0) {
        rewrittenSource += looseCode.joinToString(separator = "\n", postfix = "\n")
    }
    rewrittenSource += """${" ".repeat(snippetArguments.indent)}}""" + "\n}"

    // Add final two braces
    currentOutputLineNumber += 1
    assert(currentOutputLineNumber == rewrittenSource.lines().size)

    return Snippet(
        Sources(hashMapOf(SNIPPET_SOURCE to rewrittenSource)),
        originalSource,
        rewrittenSource,
        snippetRange,
        snippetClassName,
        "$snippetMainMethodName()",
        Source.FileType.JAVA,
        remappedLineMapping
    )
}

// Used to be used to check for return statements, but that has been moved into the walker
@Suppress("unused")
private fun checkJavaLooseCode(
    looseCode: String,
    looseCodeStart: Int,
    remappedLineMapping: Map<Int, Snippet.RemappedLine>
) {
    val charStream = CharStreams.fromString(looseCode)
    val javaLexer = JavaLexer(charStream)
    javaLexer.removeErrorListeners()
    val tokenStream = CommonTokenStream(javaLexer)
    tokenStream.fill()

    val validationErrors = tokenStream.tokens.filter {
        it.type == JavaLexer.RETURN
    }.map {
        SnippetTransformationError(
            Snippet.mapLocation(
                SourceLocation(SNIPPET_SOURCE, it.line + looseCodeStart, it.charPositionInLine),
                remappedLineMapping
            ),
            "return statements not allowed at top level in snippets"
        )
    }
    if (validationErrors.isNotEmpty()) {
        throw SnippetTransformationFailed(validationErrors)
    }
}

private const val MAX_NAME_TRIES = 64
private fun generateName(prefix: String, existingNames: Set<String>): String {
    if (!existingNames.contains(prefix)) {
        return prefix
    } else {
        for (suffix in 1..MAX_NAME_TRIES) {
            val testClassName = "$prefix$suffix"
            if (!existingNames.contains(testClassName)) {
                return testClassName
            }
        }
    }
    throw IllegalStateException("couldn't generate $prefix class name")
}
