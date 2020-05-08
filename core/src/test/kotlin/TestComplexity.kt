package edu.illinois.cs.cs125.jeed.core

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec

class TestComplexity : StringSpec({
    "should calculate complexity for snippets" {
        val complexityResults = Source.fromSnippet(
            """
int add(int i, int j) {
    return i + j;
}
int i = 0;
""".trim()
        ).complexity()
        complexityResults.lookup("").complexity shouldBe 2
        println((complexityResults.results[""]?.get("") as ClassComplexity).methods[""]?.range)
    }
    "should calculate complexity for sources" {
        val complexityResults = Source(
            mapOf(
                "Test.java" to """
public class Test {
    int add(int i, int j) {
        return i + j;
    }
}
""".trim()
            )
        ).complexity()
        complexityResults.lookup("Test.add(int,int)", "Test.java").complexity shouldBe 1
    }
    "should fail properly on parse errors" {
        shouldThrow<ComplexityFailed> {
            Source(
                mapOf(
                    "Test.java" to """
public class Test
    int add(int i, int j) {
        return i + j;
    }
}
""".trim()
                )
            ).complexity()
        }
    }
    "should calculate complexity for simple conditional statements" {
        val complexityResults = Source(
            mapOf(
                "Test.java" to """
public class Test {
    int chooser(int i, int j) {
        if (i > j) {
            return i;
        } else {
            return i;
        }
    }
}
""".trim()
            )
        ).complexity()
        complexityResults.lookup("Test.chooser(int,int)", "Test.java").complexity shouldBe 2
    }
    "should calculate complexity for complex conditional statements" {
        val complexityResults = Source(
            mapOf(
                "Test.java" to """
public class Test {
    int chooser(int i, int j) {
        if (i > j) {
            return i;
        } else if (i < j) {
            return j;
        } else if (i == j) {
            return i + j;
        } else {
            return i;
        }
    }
}
""".trim()
            )
        ).complexity()
        complexityResults.lookup("Test.chooser(int,int)", "Test.java").complexity shouldBe 4
    }
})
