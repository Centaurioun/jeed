package edu.illinois.cs.cs125.jeed.core

import io.kotlintest.specs.StringSpec
import io.kotlintest.*
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.numerics.shouldBeGreaterThan

class TestCompile : StringSpec({
    "should compile simple snippets" {
        val compiledSource = Source.transformSnippet("int i = 1;").compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("Main"))
        compiledSource should haveProvidedThisManyClasses(0)
    }
    "should compile snippets that include method definitions" {
        val compiledSource = Source.transformSnippet("""
int i = 0;
private static int main() {
    return 0;
}""".trim()).compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("Main"))
        compiledSource should haveProvidedThisManyClasses(0)
    }
    "should compile snippets that include class definitions" {
        val compiledSource = Source.transformSnippet("""
int i = 0;
public class Foo {
    int i;
}
Foo foo = new Foo();
""".trim()).compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("Main", "Foo"))
        compiledSource should haveProvidedThisManyClasses(0)
    }
    "should compile multiple sources" {
        val compiledSource = Source(mapOf(
                "Test.java" to "public class Test {}",
                "Me.java" to "public class Me {}"
        )).compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("Test", "Me"))
        compiledSource should haveProvidedThisManyClasses(0)
    }
    "should compile sources with dependencies" {
        val compiledSource = Source(mapOf(
                "Test.java" to "public class Test {}",
                "Me.java" to "public class Me extends Test {}"
        )).compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("Test", "Me"))
        compiledSource should haveProvidedThisManyClasses(0)
    }
    "should compile sources with dependencies in wrong order" {
        val compiledSource = Source(mapOf(
                "Test.java" to "public class Test extends Me {}",
                "Me.java" to "public class Me {}"
        )).compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("Test", "Me"))
        compiledSource should haveProvidedThisManyClasses(0)
    }
    "should compile sources in multiple packages" {
        val compiledSource = Source(mapOf(
                "test/Test.java" to """
package test;
public class Test {}
                """.trim(),
                "me/Me.java" to """
package me;
public class Me {}
""".trim())).compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("test.Test", "me.Me"))
        compiledSource should haveProvidedThisManyClasses(0)
    }
    "should compile sources in multiple packages with dependencies in wrong order" {
        val compiledSource = Source(mapOf(
                "test/Test.java" to """
package test;
import me.Me;
public class Test extends Me {}
""".trim(),
                "me/Me.java" to """
package me;
public class Me {}
""".trim())).compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("test.Test", "me.Me"))
        compiledSource should haveProvidedThisManyClasses(0)
    }
    "should compile sources that use Java 10 features" {
        val compiledSource = Source(mapOf(
                "Test.java" to """
public class Test {
    public static void main() {
        var i = 0;
    }
}""".trim())).compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("Test"))
        compiledSource should haveProvidedThisManyClasses(0)
    }
    "should compile sources that use inner classes" {
        val compiledSource = Source(mapOf(
                "Test.java" to """
public class Test {
    class Inner { }
    Test() {
        Inner inner = new Inner();
    }
    public static void main() {
        Test test = new Test();
    }
}""".trim())).compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("Test", "Test\$Inner"))
        compiledSource should haveProvidedThisManyClasses(0)
    }
    "should identify compilation errors in simple snippets" {
        val failedCompilation = shouldThrow<CompilationFailed> { Source.transformSnippet("int i = a;").compile() }

        failedCompilation should haveCompilationErrorAt(line=1)
    }
    "should identify multiple compilation errors in simple snippets" {
        val failedCompilation = shouldThrow<CompilationFailed> {
            Source.transformSnippet("""
int i = a;
Foo f = new Foo();
""".trim()).compile()
        }

        failedCompilation should haveCompilationErrorAt(line=1)
        failedCompilation should haveCompilationErrorAt(line=2)
    }
    "should identify multiple compilation errors in reordered simple snippets" {
        val failedCompilation = shouldThrow<CompilationFailed> {
            Source.transformSnippet("""
public void foo() {
    return;
}
public class Bar { }
int i = a;
Foo f = new Foo();
""".trim()).compile()
        }

        failedCompilation should haveCompilationErrorAt(line=5)
        failedCompilation should haveCompilationErrorAt(line=6)
    }
    "should identify warnings in snippets" {
        val compiledSource = Source.transformSnippet("""
import java.util.List;
import java.util.ArrayList;
List test = new ArrayList();
""".trim()).compile()

        compiledSource.messages shouldHaveSize 2
        compiledSource should haveCompilationMessageAt(line=3)
    }
    "should not identify warnings in snippets when warnings are disabled" {
        val compiledSource = Source.transformSnippet("""
import java.util.List;
import java.util.ArrayList;
List test = new ArrayList();
""".trim()).compile(CompilationArguments(Xlint = "none"))

        compiledSource.messages shouldHaveSize 0
    }
    "should fail when warnings are treated as errors" {
        val exception = shouldThrow<CompilationFailed> {
            Source.transformSnippet("""
import java.util.List;
import java.util.ArrayList;
List test = new ArrayList();
""".trim()).compile(CompilationArguments(wError = true))
        }

        exception should haveCompilationErrorAt(line=3)
    }
    "should enumerate and load classes correctly after execution" {
        val compiledSource = Source.transformSnippet("""
class Test {}
class Me {}
Test test = new Test();
Me me = new Me();
""".trim()).compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("Test", "Me", "Main"))
        compiledSource should haveProvidedThisManyClasses(0)
        val executionResult = compiledSource.execute()

        executionResult should haveCompleted()
        executionResult should haveDefinedExactlyTheseClasses(setOf("Test", "Me", "Main"))
        executionResult should haveProvidedExactlyTheseClasses(setOf("Test", "Me", "Main"))
        executionResult should haveLoadedAtLeastTheseClasses(setOf("java.lang.Object", "Test", "Me", "Main"))
        compiledSource.classLoader.bytecodeForClass("Test").size shouldBeGreaterThan 0
    }
    "should correctly accept previously compiled source argument" {
        val compiledTestSource = Source(
                mapOf("Test.java" to """
public class Test {}
            """.trim())).compile()
        val compiledFooSource = Source(
                    mapOf("Foo.java" to """
    public class Foo extends Test { }
            """.trim())).compileWith(compiledTestSource)

        compiledFooSource should haveDefinedExactlyTheseClasses(setOf("Foo"))
        compiledFooSource should haveProvidedThisManyClasses(0)
    }
    "should correctly accept previously compiled source argument in another package" {
        val compiledMeSource = Source(
                mapOf("test/Me.java" to """
package test;
public class Me {}
            """.trim())).compile()

        val compiledFooSource = Source(
                mapOf("another/Foo.java" to """
package another;
import test.Me;
public class Foo extends Me { }
            """.trim())).compileWith(compiledMeSource)

        compiledFooSource should haveDefinedExactlyTheseClasses(setOf("another.Foo"))
        compiledFooSource should haveProvidedThisManyClasses(0)
    }
    "should compile with classes from Java standard libraries" {
        val compiledSource = Source.transformSnippet("""
import java.util.List;
import java.util.ArrayList;

List list = new ArrayList();
""".trim()).compile()

        compiledSource should haveDefinedExactlyTheseClasses(setOf("Main"))
        compiledSource should haveProvidedThisManyClasses(0)
    }
    "should compile with classes from nonstandard libraries" {
        val compiledSource = Source.transformSnippet("""
import com.puppycrawl.tools.checkstyle.Checker;

System.out.println(new Checker());
""".trim()).compile()
        compiledSource should haveDefinedExactlyTheseClasses(setOf("Main"))
    }
    "should compile with classes from .class files" {
        val compiledSource = Source.transformSnippet("""
import edu.illinois.cs.cs125.testingjeed.importable.*;

Widget w = new Widget();
""".trim()).compile()
        compiledSource should haveDefinedExactlyTheseClasses(setOf("Main"))
    }
    "should compile sources that use Java 12 features" {
        if (systemCompilerVersion < 12) {
            // throw SkipTestException("Cannot run this test until Java 12")
        } else {
            val compiledSource = Source(mapOf(
                    "Test.java" to """
public class Test {
    public static String testYieldKeyword(int switchArg) {
        return switch (switchArg) {
            case 1, 2 -> "works";
            case 3 -> "oh boy";
            default -> "testing";
        };
    }
    public static void main() {
        System.out.println(testYieldKeyword(1));
    }
}""".trim())).compile()

            compiledSource should haveDefinedExactlyTheseClasses(setOf("Test"))
            compiledSource should haveProvidedThisManyClasses(0)
        }
    }
    "should compile sources that use Java 13 features" {
        if (systemCompilerVersion < 12) {
            // throw SkipTestException("Cannot run this test until Java 13")
        } else {
            val compiledSource = Source(mapOf(
                    "Test.java" to """
public class Test {
    public static String testYieldKeyword(int switchArg) {
        return switch (switchArg) {
            case 1, 2: yield "works";
            case 3: yield "oh boy";
            default: yield "testing";
        };
    }
    public static void main() {
        System.out.println(testYieldKeyword(1));
    }
}""".trim())).compile()

            compiledSource should haveDefinedExactlyTheseClasses(setOf("Test"))
            compiledSource should haveProvidedThisManyClasses(0)
        }
    }
})

fun haveCompilationErrorAt(source: String = SNIPPET_SOURCE, line: Int) = object : Matcher<CompilationFailed> {
    override fun test(value: CompilationFailed): MatcherResult {
        return MatcherResult(
                value.errors.any { it.location.source == source && it.location.line == line },
                "should have compilation error on line $line",
                "should not have compilation error on line $line"
        )
    }
}
fun haveCompilationMessageAt(source: String = SNIPPET_SOURCE, line: Int) = object : Matcher<CompiledSource> {
    override fun test(value: CompiledSource): MatcherResult {
        return MatcherResult(
                value.messages.any { it.location.source == source && it.location.line == line },
                "should have compilation message on line $line",
                "should not have compilation message on line $line"
        )
    }
}
fun <T> haveDefinedExactlyTheseClasses(classes: Set<String>) = object : Matcher<T> {
    override fun test(value: T): MatcherResult {
        val definedClasses = when (value) {
            is CompiledSource -> value.classLoader.definedClasses
            is Sandbox.TaskResults<*> -> value.sandboxedClassLoader!!.definedClasses
            else -> error("invalid type")
        }
        return MatcherResult(
                definedClasses == classes,
                "should have defined ${ classes.joinToString(separator = ", ")} (found ${ definedClasses.joinToString(separator = ", ") })",
                "should not have defined ${ classes.joinToString(separator = ", ")}"
        )
    }
}
fun <T> haveProvidedThisManyClasses(count: Int) = object : Matcher<T> {
    override fun test(value: T): MatcherResult {
        val providedClassCount = when (value) {
            is CompiledSource -> value.classLoader.providedClasses.size
            is Sandbox.TaskResults<*> -> value.sandboxedClassLoader!!.providedClasses.size
            else -> error("invalid type")
        }
        return MatcherResult(
                providedClassCount == count,
                "should have loaded $count classes (found $providedClassCount)",
                "should not have loaded $count classes"
        )
    }
}
fun <T> haveProvidedExactlyTheseClasses(classes: Set<String>) = object : Matcher<T> {
    override fun test(value: T): MatcherResult {
        val providedClasses = when (value) {
            is CompiledSource -> value.classLoader.providedClasses
            is Sandbox.TaskResults<*> -> value.sandboxedClassLoader!!.providedClasses
            else -> error("invalid type")
        }
        return MatcherResult(
                providedClasses == classes,
                "should have provided ${ classes.joinToString(separator = ", ")} (found ${ providedClasses.joinToString(separator = ", ") })",
                "should not have provided ${ classes.joinToString(separator = ", ")}"
        )
    }
}
fun <T> haveLoadedAtLeastTheseClasses(classes: Set<String>) = object : Matcher<T> {
    override fun test(value: T): MatcherResult {
        val loadedClasses = when (value) {
            is CompiledSource -> value.classLoader.loadedClasses
            is Sandbox.TaskResults<*> -> value.sandboxedClassLoader!!.loadedClasses
            else -> error("invalid type")
        }
        return MatcherResult(
                loadedClasses.containsAll(classes),
                "should have loaded at least ${ classes.joinToString(separator = ", ")} (found ${ loadedClasses.joinToString(separator = ", ") })",
                "should not have loaded at least ${ classes.joinToString(separator = ", ")}"
        )
    }
}
