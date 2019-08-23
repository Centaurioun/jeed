package edu.illinois.cs.cs125.jeed.core

import io.kotlintest.specs.StringSpec
import io.kotlintest.*

class TestCheckstyle : StringSpec({
    "it should check strings without errors" {
        val checkstyleResult = Source.transformSnippet("""
int i = 0;
""".trim()).checkstyle()

        checkstyleResult shouldNot haveCheckstyleErrors()
    }
    "it should identify checkstyle errors in strings" {
        val checkstyleErrors = Source.transformSnippet("""
int i = 0;
int y =1;
""".trim()).checkstyle()

        checkstyleErrors should haveCheckstyleErrors()
        checkstyleErrors should haveCheckstyleErrorAt(line=2)
    }
    "it should identify checkstyle errors in snippet results" {
        val checkstyleErrors = Source.transformSnippet("""
int i = 0;
int y = 1;
int add(int a, int b) {
    return a+ b;
}
add(i, y);
""".trim()).checkstyle()

        checkstyleErrors should haveCheckstyleErrors()
        checkstyleErrors should haveCheckstyleErrorAt(line=4)
    }
    "it should identify checkstyle errors in snippet static results" {
        val checkstyleErrors = Source.transformSnippet("""
int i = 0;
int y = 1;
static int add(int a, int b) {
    return a+ b;
}
add(i, y);
""".trim()).checkstyle()

        checkstyleErrors should haveCheckstyleErrors()
        checkstyleErrors should haveCheckstyleErrorAt(line=4)
    }
    "it should identify checkstyle errors in snippet results with modifiers" {
        val checkstyleErrors = Source.transformSnippet("""
int i = 0;
int y = 1;
public int add(int a, int b) {
    return a+ b;
}
add(i, y);
""".trim()).checkstyle()

        checkstyleErrors should haveCheckstyleErrors()
        checkstyleErrors should haveCheckstyleErrorAt(line=4)
    }
})

fun haveCheckstyleErrors() = object : Matcher<CheckstyleResults> {
    override fun test(value: CheckstyleResults): MatcherResult {
        return MatcherResult(value.errors.values.flatten().isNotEmpty(),
                "should have checkstyle errors",
                "should have checkstyle errors")
    }
}
fun haveCheckstyleErrorAt(source: String = SNIPPET_SOURCE, line: Int) = object : Matcher<CheckstyleResults> {
    override fun test(value: CheckstyleResults): MatcherResult {
        return MatcherResult(value.errors.values.flatten().any { it.location.source == source && it.location.line == line },
                "should have checkstyle error on line $line",
                "should not have checkstyle error on line $line")
    }
}
