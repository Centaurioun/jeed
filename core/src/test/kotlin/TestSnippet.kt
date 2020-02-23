package edu.illinois.cs.cs125.jeed.core

import io.kotlintest.Matcher
import io.kotlintest.MatcherResult
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec

class TestSnippet : StringSpec({
    "should parse snippets" {
        Source.transformSnippet(
            """
import java.util.List;

class Test {
    int me = 0;
    int anotherTest() {
        return 8;
    }
}
int testing() {
    int j = 0;
    return 10;
}
class AnotherTest { }
int i = 0;
i++;""".trim()
        )
    }
    "should identify a parse errors in a broken snippet" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.transformSnippet(
                """
class Test {
    int me = 0;
    int anotherTest() {
      return 8;
    }
}
int testing() {
    int j = 0;
    return 10;
}
int i = 0;
i++
""".trim()
            )
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(12)
    }
    "should identify multiple parse errors in a broken snippet" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.transformSnippet(
                """
class;
class Test {
    int me = 0;
    int anotherTest() {
      return 8;
    }
}
int testing() {
    int j = 0;
    return 10;
}
int i = 0;
i++
""".trim()
            )
        }
        exception.errors shouldHaveSize 2
        exception should haveParseErrorOnLine(1)
        exception should haveParseErrorOnLine(13)
    }
    "should be able to reconstruct original sources using entry map" {
        val snippet = """
int i = 0;
i++;
public class Test {}
int adder(int first, int second) {
    return first + second;
}
        """.trim()
        val source = Source.transformSnippet(snippet)

        source.originalSource shouldBe (snippet)
        source.rewrittenSource shouldNotBe (snippet)
        source.originalSourceFromMap() shouldBe (snippet)
    }
    "should not allow return statements in loose code" {
        shouldThrow<SnippetTransformationFailed> {
            Source.transformSnippet(
                """
return;
        """.trim()
            )
        }
    }
    "should not allow return statements in loose code even under if statements" {
        shouldThrow<SnippetTransformationFailed> {
            Source.transformSnippet(
                """
int i = 0;
if (i > 2) {
    return;
}
        """.trim()
            )
        }
    }
    "should add static to methods that lack static" {
        Source.transformSnippet(
                """
void test0() {
  System.out.println("Hello, world!");
}
public void test1() {
  System.out.println("Hello, world!");
}
private void test2() {
  System.out.println("Hello, world!");
}
protected void test3() {
  System.out.println("Hello, world!");
}
  public void test4() {
  System.out.println("Hello, world!");
}
        """.trim()
        ).compile()
    }
    // TODO: Update if and when ANTLR4 grammar is updated
    "!should parse Java 13 constructs in snippets" {
        Source.transformSnippet(
            """
static String test(int arg) {
  switch (arg) {
      case 0 -> "test";
      default -> "whatever";
  }
}
System.out.println(test(0));
        """.trim()
        )
    }
    "should not allow package declarations in snippets" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.transformSnippet(
                """
package test.me;

System.out.println("Hello, world!");
        """.trim()
            )
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(1)
    }
    "should reject imports not at top of snippet" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.transformSnippet(
                """
public class Foo { }
System.out.println("Hello, world!");
import java.util.List;
        """.trim()
            )
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(3)
    }
})

fun haveParseErrorOnLine(line: Int) = object : Matcher<SnippetTransformationFailed> {
    override fun test(value: SnippetTransformationFailed): MatcherResult {
        return MatcherResult(
            value.errors.any { it.location.line == line },
            "should have parse error on line $line",
            "should not have parse error on line $line"
        )
    }
}
